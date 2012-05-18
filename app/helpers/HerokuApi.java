package helpers;

import com.heroku.api.Addon;
import com.heroku.api.App;
import com.heroku.api.Heroku;
import com.heroku.api.HerokuAPI;
import com.heroku.api.exception.RequestFailedException;

import java.util.List;

import static java.lang.System.getenv;

/**
* @author mh
* @since 31.12.11
*/
public class HerokuApi {
    private final HerokuAPI herokuAPI;

    public HerokuApi() {
        this(getenv("HEROKU_USERNAME"),getenv("HEROKU_PASSWORD"));
    }
    public HerokuApi(final String username, final String password) {
        herokuAPI = createConnection(username, password);
    }
    public HerokuApi(final String token) {
        herokuAPI = createApi(token);
    }

    private HerokuAPI createConnection(final String username, final String password) {
        final String token = HerokuAPI.obtainApiKey(username, password);
        return createApi(token);
    }

    private HerokuAPI createApi(final String token) {
        return new HerokuAPI(token);
    }

    public void shareApp(App app, final String emailAddress) {
        herokuAPI.addCollaborator(app.getName(),emailAddress);
    }

    public void removeHerokuUserCollaborator(final String appName, final String collaboratorEmail) {
        herokuAPI.removeCollaborator(appName,collaboratorEmail);
    }

    public void removeHerokuUserKey(final String keyName) {
        herokuAPI.removeKey(keyName);
    }

    public void addPublicKey(String sshPublicKey) {
        herokuAPI.addKey(sshPublicKey);
    }

    public App createApp(final Heroku.Stack stack) {
        final App app = herokuAPI.createApp(new App().on(stack));

        if (!app.getCreateStatus().equals("complete")) {
            throw new RuntimeException("Could not create the Heroku app");
        }
        return app;
    }

    public void transferApp(final String emailAddress, final String appName) {
        herokuAPI.transferApp(appName,emailAddress);
    }


    public List<Addon> listAddons() {
        return herokuAPI.listAllAddons();
    }

    public static String getToken(String email, String password) {
        try {
            return HerokuAPI.obtainApiKey(email, password);
        } catch(RequestFailedException rfe) {
            return null;
        }
    }

    public List<App> listApps() {
        return herokuAPI.listApps();
    }

    public List<Addon> addonsFor(App app) {
        return herokuAPI.listAppAddons(app.getName());
    }
}
