package helpers;

import com.heroku.api.Heroku;
import com.heroku.api.connection.HttpClientConnection;
import com.heroku.api.model.App;
import com.heroku.api.request.app.AppCreate;
import com.heroku.api.request.key.KeyAdd;
import com.heroku.api.request.key.KeyRemove;
import com.heroku.api.request.login.BasicAuthLogin;
import com.heroku.api.request.sharing.SharingAdd;
import com.heroku.api.request.sharing.SharingRemove;
import com.heroku.api.request.sharing.SharingTransfer;
import com.heroku.api.response.Unit;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import play.jobs.Job;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

import static java.lang.System.getenv;

public class HerokuAppSharingHelper extends Job<App> {

    private static final String SSH_KEY_COMMENT = "share@heroku";

    private final String emailAddress;
    private final String gitUrl;

    public Throwable exception;

    private final HttpClientConnection herokuConnection;

    public HerokuAppSharingHelper(String emailAddress, String gitUrl) {
        this.emailAddress = emailAddress;
        this.gitUrl = gitUrl;
        herokuConnection = createConnection(userName(), getenv("HEROKU_PASSWORD"));
    }

    private HttpClientConnection createConnection(final String username, final String password) {
        return new HttpClientConnection(new BasicAuthLogin(username, password));
    }

    @Override
    public App doJobWithResult() throws Exception {
        File home = createHome();
        addSshKeysAndConfig(home);

        App app = cloneRepoAndPush(home, gitUrl);

        transferAppToUser(app, emailAddress);

        cleanUp(app, home);

        return app;
    }

    private void cleanUp(App app, File home) {
        removeHerokuUserCollaborator(app);
        removeHerokuUserKey();

        cleanUp(home);
    }

    private void transferAppToUser(App app, final String emailAddress) {
        shareApp(app, herokuConnection, emailAddress);
        transferApp(app, emailAddress);
    }

    private App cloneRepoAndPush(File home, final String gitUrl) throws URISyntaxException, IOException, WrongRepositoryStateException, InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException, RefNotFoundException {
        Git gitRepo = cloneGitRepository(home, gitUrl);
        setRepoUserHome(home, gitRepo);

        App app = createApp(Heroku.Stack.Cedar);
        pushToRemote(gitRepo, app.getGit_url());
        return app;
    }

    private void addSshKeysAndConfig(File home) throws JSchException, IOException {
        File sshDir = sshDir(home);
        String publicKey = createKeys(sshDir);
        copyKnownHosts(sshDir);
        addPublicKey(publicKey);
    }

    private void cleanUp(File home) {
        home.delete();
    }

    private void removeHerokuUserKey() {
        KeyRemove keyRemove = new KeyRemove(SSH_KEY_COMMENT);
        Unit keyRemoveResponse = herokuConnection.execute(keyRemove);

        if (keyRemoveResponse == null) {
            throw new RuntimeException("Could not remove ssh key");
        }
    }

    private void removeHerokuUserCollaborator(App app) {
        SharingRemove sharingRemove = new SharingRemove(app.getName(), userName());
        Unit sharingRemoveResponse = herokuConnection.execute(sharingRemove);

        if (sharingRemoveResponse == null) {
            throw new RuntimeException("Could remove " + userName() + " from the app");
        }
    }

    private String userName() {
        return getenv("HEROKU_USERNAME");
    }

    private void transferApp(App app, final String emailAddress) {
        SharingTransfer sharingTransfer = new SharingTransfer(app.getName(), emailAddress);
        Unit sharingTransferResponse = herokuConnection.execute(sharingTransfer);

        if (sharingTransferResponse == null) {
            throw new RuntimeException("Could not transfer the app to " + emailAddress);
        }
    }

    private void setRepoUserHome(File home, Git gitRepo) {
        gitRepo.getRepository().getFS().setUserHome(home);
    }

    private void pushToRemote(Git gitRepo, final String appGitUrl) throws InvalidRemoteException {
        gitRepo.push().setRemote(appGitUrl).call();
    }

    private File sshDir(File home) {
        File sshDir = new File(home, ".ssh");
        if (sshDir.exists()) return sshDir;
        sshDir.mkdirs();
        return sshDir;
    }

    private void copyKnownHosts(File sshDir) throws IOException {
        File knownHostsFile = new File(getClass().getClassLoader().getResource("known_hosts").getFile());
        FileUtils.copyFileToDirectory(knownHostsFile, sshDir);
    }

    private String createKeys(File sshDir) throws JSchException, IOException {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA);
        keyPair.writePrivateKey(new File(sshDir, "id_rsa").getAbsolutePath());
        keyPair.writePublicKey(new File(sshDir, "id_rsa.pub").getAbsolutePath(), SSH_KEY_COMMENT);

        ByteArrayOutputStream publicKeyOutputStream = new ByteArrayOutputStream();
        keyPair.writePublicKey(publicKeyOutputStream, SSH_KEY_COMMENT);
        publicKeyOutputStream.close();
        return new String(publicKeyOutputStream.toByteArray());
    }

    private File createHome() {
        final File home = new File(tmpDir(), UUID.randomUUID().toString());
        if (home.exists()) throw new RuntimeException("Home Directory " + home + " already exists");
        if (!home.mkdirs()) throw new RuntimeException("Cannot create Home Directory " + home);
        home.deleteOnExit();
        return home;
    }

    private Git cloneGitRepository(File home, final String gitUrl) throws URISyntaxException, IOException, WrongRepositoryStateException, InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException, RefNotFoundException {
        File srcDir = new File(home, "src");

        if (srcDir.exists()) throw new RuntimeException("Source directory " + srcDir + " already exists!");

        return new CloneCommand().setURI(gitUrl).setDirectory(srcDir).call();
    }

    private String tmpDir() {
        return System.getProperty("java.io.tmpdir");
    }

    private void addPublicKey(String sshPublicKey) {
        KeyAdd keyAdd = new KeyAdd(sshPublicKey);
        Unit keyAddResponse = herokuConnection.execute(keyAdd);

        if (keyAddResponse == null) {
            throw new RuntimeException("Could not add an ssh key to the user");
        }
    }

    private App createApp(final Heroku.Stack stack) {
        App app;
        AppCreate cmd = new AppCreate(stack);
        app = herokuConnection.execute(cmd);

        if (!app.getCreate_status().equals("complete")) {
            throw new RuntimeException("Could not create the Heroku app");
        }
        return app;
    }

    private void shareApp(App app, HttpClientConnection herokuConnection, final String emailAddress) {
        SharingAdd sharingAdd = new SharingAdd(app.getName(), emailAddress);
        Unit sharingAddResponse = herokuConnection.execute(sharingAdd);

        if (sharingAddResponse == null) {
            throw new RuntimeException("Could not add " + emailAddress + " as a collaborator");
        }
    }

    @Override
    public void onException(Throwable e) {
        this.exception = e;
    }
}