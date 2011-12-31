package controllers;

import com.dmurph.tracking.AnalyticsConfigData;
import com.dmurph.tracking.JGoogleAnalyticsTracker;
import com.google.gson.Gson;
import com.heroku.api.model.App;
import helpers.EmailHelper;
import helpers.HerokuAppSharingHelper;
import play.data.validation.Validation;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Http;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

public class ShareApp extends Controller {

    private static final AnalyticsConfigData config = new AnalyticsConfigData("UA-26859570-1");

    public static void shareApp(String emailAddress, String gitUrl) {
        final Validation.ValidationResult emailValidation = validation.email(emailAddress);
        final Validation.ValidationResult urlValidation = validation.minSize(gitUrl, 1);//todo: convert to matcher

        if (emailValidation.ok && urlValidation.ok) {
            trackShareApp(gitUrl);
            try {
                response.setContentTypeIfNotSet("application/json; charset=" + Http.Response.current().encoding);

                App app = shareAppInBackground(emailAddress, gitUrl);

                response.writeChunk(toJson(singletonMap("result", app)));
            } catch (Throwable e) {
                e.printStackTrace();
                sendErrorEmail(e);
                response.writeChunk(toJson(singletonMap("error", singletonMap("shareApp", e.getMessage()))));
            }
        } else {
            renderErrors(emailValidation, urlValidation);
        }
    }

    private static String toJson(Object value) {
        return new Gson().toJson(value);
    }

    private static void sendErrorEmail(Throwable e) {
        try {
            EmailHelper.sendEmailViaMailGun(System.getenv("HEROKU_USERNAME"), System.getenv("HEROKU_USERNAME"), "App Error: " + request.host, e.getMessage() + "\r\n" + toString(e));
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static String toString(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void trackShareApp(String gitUrl) {
        JGoogleAnalyticsTracker tracker = new JGoogleAnalyticsTracker(config, JGoogleAnalyticsTracker.GoogleAnalyticsVersion.V_4_7_2);
        tracker.trackEvent("app", "shareApp", gitUrl);
    }

    private static App shareAppInBackground(String emailAddress, String gitUrl) throws Throwable {
        HerokuAppSharingHelper job = new HerokuAppSharingHelper(emailAddress, gitUrl);
        F.Promise<App> promiseAppMetadata = job.now();

        keepConnectionOpen(promiseAppMetadata);

        App app = await(promiseAppMetadata);

        if (job.exception != null) {
            throw job.exception;
        }
        return app;
    }

    private static void keepConnectionOpen(F.Promise<App> promiseAppMetadata) throws InterruptedException {
        while (!promiseAppMetadata.isDone()) {
            Thread.sleep(1000);
            response.writeChunk("");
        }
    }

    private static void renderErrors(Validation.ValidationResult... validationErrors) {
        Map<String, String> errors = new HashMap<String, String>();
        for (Validation.ValidationResult validationResult : validationErrors) {
            addError(validationResult, errors);
        }
        renderJSON(singletonMap("error", errors));
    }

    private static void addError(Validation.ValidationResult validationResult, Map<String, String> errors) {
        if (validationResult.ok) return;
        errors.put(validationResult.error.getKey(), validationResult.error.message());
    }
}
