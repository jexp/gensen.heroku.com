package helpers;

import com.heroku.api.Heroku;
import com.heroku.api.model.App;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.KnownHosts;
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

    private HerokuApi herokuApi;

    private static final String SSH_KEY_COMMENT = "share@heroku";

    private final String emailAddress;
    private final String gitUrl;

    public Throwable exception;


    public HerokuAppSharingHelper(String emailAddress, String gitUrl) {
        this.emailAddress = emailAddress;
        this.gitUrl = gitUrl;
        herokuApi = new HerokuApi(userName(), password());
    }

    private String password() {
        return getenv("HEROKU_PASSWORD");
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
        herokuApi.removeHerokuUserCollaborator(app.getName(), userName());
        herokuApi.removeHerokuUserKey(SSH_KEY_COMMENT);

        cleanUp(home);
    }

    private void transferAppToUser(App app, final String emailAddress) {
        herokuApi.shareApp(app, emailAddress);
        herokuApi.transferApp(emailAddress, app.getName());
    }

    private App cloneRepoAndPush(File home, final String gitUrl) throws URISyntaxException, IOException, WrongRepositoryStateException, InvalidConfigurationException, DetachedHeadException, InvalidRemoteException, CanceledException, RefNotFoundException {
        Git gitRepo = cloneGitRepository(home, gitUrl);
        setRepoUserHome(home, gitRepo);

        App app = herokuApi.createApp(Heroku.Stack.Cedar);
        pushToRemote(gitRepo, app.getGit_url());
        return app;
    }

    private void addSshKeysAndConfig(File home) throws JSchException, IOException {
        File sshDir = sshDir(home);
        String publicKey = createKeys(sshDir);
        copyKnownHosts(sshDir);
        copySshConfig(sshDir);
        herokuApi.addPublicKey(publicKey);
    }

    private void cleanUp(File home) {
        home.delete();
    }

    private String userName() {
        return getenv("HEROKU_USERNAME");
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
    private void copySshConfig(File sshDir) throws IOException {
        File sshConfigFile = new File(getClass().getClassLoader().getResource("config").getFile());
        FileUtils.copyFileToDirectory(sshConfigFile, sshDir);
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

    @Override
    public void onException(Throwable e) {
        this.exception = e;
    }
}