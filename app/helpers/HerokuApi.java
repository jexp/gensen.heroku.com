package helpers;

import com.heroku.api.Heroku;
import com.heroku.api.connection.HttpClientConnection;
import com.heroku.api.model.Addon;
import com.heroku.api.model.App;
import com.heroku.api.request.Request;
import com.heroku.api.request.addon.AddonList;
import com.heroku.api.request.app.AppCreate;
import com.heroku.api.request.key.KeyAdd;
import com.heroku.api.request.key.KeyRemove;
import com.heroku.api.request.login.BasicAuthLogin;
import com.heroku.api.request.sharing.SharingAdd;
import com.heroku.api.request.sharing.SharingRemove;
import com.heroku.api.request.sharing.SharingTransfer;
import com.heroku.api.response.Unit;

import java.util.List;

import static java.lang.System.getenv;

/**
* @author mh
* @since 31.12.11
*/
public class HerokuApi {
    private final HttpClientConnection herokuConnection;

    public HerokuApi() {
        this(getenv("HEROKU_USERNAME"),getenv("HEROKU_PASSWORD"));
    }
    public HerokuApi(final String username, final String password) {
        herokuConnection = createConnection(username, password);
    }
    private HttpClientConnection createConnection(final String username, final String password) {
        return new HttpClientConnection(new BasicAuthLogin(username, password));
    }

    public void shareApp(App app, final String emailAddress) {
        SharingAdd sharingAdd = new SharingAdd(app.getName(), emailAddress);
        Unit sharingAddResponse = herokuConnection.execute(sharingAdd);

        if (sharingAddResponse == null) {
            throw new RuntimeException("Could not add " + emailAddress + " as a collaborator");
        }
    }

    public void removeHerokuUserCollaborator(final String appName, final String collaboratorEmail) {
        SharingRemove sharingRemove = new SharingRemove(appName, collaboratorEmail);
        Unit sharingRemoveResponse = execute(sharingRemove);

        if (sharingRemoveResponse == null) {
            throw new RuntimeException("Could remove " + collaboratorEmail + " from the app");
        }
    }

    public void removeHerokuUserKey(final String keyName) {
        KeyRemove keyRemove = new KeyRemove(keyName);
        Unit keyRemoveResponse = execute(keyRemove);

        if (keyRemoveResponse == null) {
            throw new RuntimeException("Could not remove ssh key");
        }
    }

    public void addPublicKey(String sshPublicKey) {
        KeyAdd keyAdd = new KeyAdd(sshPublicKey);
        Unit keyAddResponse = execute(keyAdd);

        if (keyAddResponse == null) {
            throw new RuntimeException("Could not add an ssh key to the user");
        }
    }

    public App createApp(final Heroku.Stack stack) {
        AppCreate cmd = new AppCreate(stack);
        App app = execute(cmd);

        if (!app.getCreate_status().equals("complete")) {
            throw new RuntimeException("Could not create the Heroku app");
        }
        return app;
    }

    public void transferApp(final String emailAddress, final String appName) {
        SharingTransfer sharingTransfer = new SharingTransfer(appName, emailAddress);
        Unit sharingTransferResponse = execute(sharingTransfer);

        if (sharingTransferResponse == null) {
            throw new RuntimeException("Could not transfer the app to " + emailAddress);
        }
    }

    private <T> T execute(Request<T> request) {
        return herokuConnection.execute(request);
    }


    public List<Addon> listAddons() {
        final AddonList addonList = new AddonList();
        return execute(addonList);
    }
}
