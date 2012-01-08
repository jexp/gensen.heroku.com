package controllers;

import com.google.gson.Gson;
import com.heroku.api.model.Addon;
import com.heroku.api.model.App;
import domain.AppInfo;
import domain.Category;
import domain.RepositoryService;
import helpers.HerokuApi;
import helpers.ProcessException;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;
import play.mvc.Controller;
import play.mvc.Http;

import java.util.*;

import static java.lang.System.getenv;

public class Application extends Controller {

    private static final RestGraphDatabase gdb = new RestGraphDatabase(neo4jUrl(), getenv("NEO4J_LOGIN"), getenv("NEO4J_PASSWORD"));
    private static final RepositoryService service = new RepositoryService(gdb, new RestCypherQueryEngine(gdb.getRestAPI()));
    private static final String COMMA_SPLIT = "\\s*,\\s*";

    private static String neo4jUrl() {
        final String envUrl = getenv("NEO4J_REST_URL");
        return envUrl != null ? envUrl : "http://localhost:7474/db/data";
    }

    public static void reindex() {
        response.setContentTypeIfNotSet("application/json; charset=" + Http.Response.current().encoding);
        List<String> names = service.reindexApps();
        response.writeChunk(toJson(names));
    }

    private static void index() {
        index(null, null);
    }
    
    public static void show(Integer id) {
        final AppInfo app = service.getAppInfo(id);
        final Map<String, Category> categories = service.loadCategories();
        render(app,categories);
    }

    public static void index(String tags, String q) {
        final long start = System.currentTimeMillis();
        final Map<String, Category> categories = service.loadCategories();
        final Collection<domain.AppInfo> apps = service.loadApps(parseTagsString(tags), q).values();
        final long delta = System.currentTimeMillis() - start;
        render(categories, apps, tags, q, delta);
    }

    static Map<String, Category> parseTagsString(String tags) {
        if (tags == null || tags.trim().isEmpty()) return null;
        Map<String, Category> result = new HashMap<String, Category>();
        for (String catTag : tags.trim().split(COMMA_SPLIT)) {
            final String[] split = catTag.split("/");
            final String tagName = split[1];
            final String catName = split[0];
            if (!result.containsKey(catName)) result.put(catName, new Category(catName, null));
            result.get(catName).addTag(null, tagName, 0);
        }
        return result;
    }

    public static void add() {
        Map<String, Category> categories = service.loadCategories();
        final Collection<AppInfo> sharedApps = sharedApps();
        final List<AppInfo> apps = listApps(sharedApps);
        render(categories,apps,sharedApps);
    }

    private static Collection<AppInfo> sharedApps() {
        if (!loggedIn()) return Collections.emptyList();
        return service.loadApps(null, email()).values();
    }


    public static void addApp(String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
                              String framework, String build, String addon, String email) {
        service.addApplication(name, repository, giturl, herokuapp, stack, type, language, framework, build, addon, email);
        index();
    }
    public static void updateApp(Integer id,String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
                              String framework, String build, String addon) {
        service.updateApplication(id, name, repository, giturl, herokuapp, stack, type, language, framework, build, addon);
        index();
    }


    public static void addAddons() {
        final HerokuApi herokuApi = new HerokuApi();
        final Map<String, Category> categories = service.loadCategories("addon");
        final Category category = categories.get("addon");
        for (Addon addon : herokuApi.listAddons()) {
            String name = addonName(addon);
            if (category.containsTag(name)) continue;
            service.createTagNode(category, name);
        }
    }

    private static String addonName(Addon addon) {
        String name = addon.getName();
        if (name.contains(":")) return name.substring(0, name.indexOf(":"));
        return name;
    }

    private static String toJson(Object value) {
        return new Gson().toJson(value);
    }

    public static void login(String email, String password) {
        final String token = HerokuApi.getToken(email, password);
        if (token!=null) {
            session.put("email",email);
            session.put("token",token);
        }
        index(); // todo redirect
    }
    public static void logout() {
        session.remove("email","token");
        index(); // todo redirect
    }
    private static String email() {
        return session.get("email");
    }
    private static String token() {
        return session.get("token");
    }
    private static HerokuApi herokuApi() {
        if (!loggedIn()) throw new ProcessException("Not Logged In");
        return new HerokuApi(token());
    }
    private static boolean loggedIn() {
        return email()!=null;
    }
    private static List<AppInfo> listApps(Collection<AppInfo> sharedApps) {
        if (!loggedIn()) return Collections.emptyList();
        List<AppInfo> result=new ArrayList<AppInfo>();
        final HerokuApi api = herokuApi();

        for (App herokuApp : api.listApps()) {
            if (isShared(sharedApps, herokuApp)) continue;
            final AppInfo appInfo = new AppInfo(null, herokuApp.getName(), herokuApp.getWeb_url(), null, toStack(herokuApp), null);
            for (Addon addon : api.addonsFor(herokuApp)) {
                appInfo.addTag("addon",addonName(addon));
            }
            result.add(appInfo);
        }
        return result;
    }

    private static boolean isShared(Collection<AppInfo> sharedApps, App app) {
        final String url = app.getWeb_url();
        for (AppInfo sharedApp : sharedApps) {
            if (sharedApp.getUrl().equals(url)) return true;
        }
        return false;
    }

    private static String toStack(App herokuApp) {
        final String stack = herokuApp.getStack();
        if (stack.equalsIgnoreCase("cedar")) return "cedar";
        if (stack.contains("amboo")) return "bamboo";
        if (stack.contains("spen")) return "aspen";
        return null;
    }
}
