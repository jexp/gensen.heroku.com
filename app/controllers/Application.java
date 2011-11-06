package controllers;

import com.google.gson.Gson;
import helpers.HerokuAppSharingHelper;
import models.AppMetadata;
import play.*;
import play.libs.F;
import play.mvc.*;
import play.data.validation.Error;
import play.mvc.results.RenderJson;

import java.util.*;

public class Application extends Controller {

    public static void index() {
        render();
    }

    public static void shareApp(String emailAddress, String gitUrl) {
        validation.email(emailAddress);
        validation.minSize(gitUrl, 1); //todo: convert to matcher

        if(validation.hasErrors()) {
            Map<String, Map<String, String>> errors = new HashMap<String, Map<String, String>>();
            errors.put("error", new HashMap<String, String>());
            for (Error error : validation.errors()) {
                errors.get("error").put(error.getKey(), error.message());
            }
            renderJSON(errors);
        }
        else {
            
            try {
                F.Promise<AppMetadata> promiseAppMetadata = new HerokuAppSharingHelper(emailAddress, gitUrl).now();

                String encoding = Http.Response.current().encoding;
                response.setContentTypeIfNotSet("application/json; charset=" + encoding);

                // force this connection to stay open
                while (!promiseAppMetadata.isDone()) {
                    Thread.sleep(1);
                    response.writeChunk("");
                }

                AppMetadata appMetadata = await(promiseAppMetadata);
                
                Map<String, AppMetadata> result = new HashMap<String, AppMetadata>();
                result.put("result", appMetadata);

                response.writeChunk(new Gson().toJson(result));
            }
            catch (RuntimeException e) {
                Map<String, Map<String, String>> errors = new HashMap<String, Map<String, String>>();
                errors.put("error", new HashMap<String, String>());
                errors.get("error").put("shareApp", e.getMessage());
                renderJSON(errors);
            }
            catch (InterruptedException e) {
                Map<String, Map<String, String>> errors = new HashMap<String, Map<String, String>>();
                errors.put("error", new HashMap<String, String>());
                errors.get("error").put("shareApp", e.getMessage());
                renderJSON(errors);
            }
            
        }
    }

}