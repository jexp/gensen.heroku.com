package controllers;

import com.google.gson.Gson;
import com.heroku.api.model.Addon;
import domain.AppInfo;
import domain.Category;
import domain.RepositoryService;
import helpers.HerokuApi;
import play.mvc.Controller;
import play.mvc.Http;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.getenv;

public class Application extends Controller {

    private static final RepositoryService service = new RepositoryService(neo4jUrl(), getenv("NEO4J_LOGIN"), getenv("NEO4J_PASSWORD"));
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
    
    public static void show(Long id) {
        final AppInfo app = service.getAppInfo(id);
        render(app);
    }

    public static void index(String tags, String q) {
        final long start = System.currentTimeMillis();
        final Map<String, Category> categories = service.loadCategories();
        final Collection<domain.AppInfo> apps = service.loadApps(parseTagsString(tags), q).values();
        final long delta = System.currentTimeMillis() - start;
        render(categories, apps, tags, q, delta);
    }

    private static Map<String, Category> parseTagsString(String tags) {
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
        render(categories);
    }


    public static void addApp(String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
                              String framework, String build, String addOn, String email) {
        service.addApplication(name, repository, giturl, herokuapp, stack, type, language, framework, build, addOn, email);
        index();
    }


    public static void addAddons() {
        final HerokuApi herokuApi = new HerokuApi();
        final Map<String, Category> categories = service.loadCategories("add-on");
        final Category category = categories.get("add-on");
        for (Addon addon : herokuApi.listAddons()) {
            String name = addon.getName();
            if (name.contains(":")) name = name.substring(0, name.indexOf(":"));
            if (category.containsTag(name)) continue;
            service.createTagNode(category, name);
        }
    }

    private static String toJson(Object value) {
        return new Gson().toJson(value);
    }
}
