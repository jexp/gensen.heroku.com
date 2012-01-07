package controllers;

import domain.Category;
import domain.RepositoryService;
import helpers.ProcessException;
import org.neo4j.graphdb.Node;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;
import play.mvc.Controller;
import play.mvc.Http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.lang.System.getenv;

public class REST extends Controller {

    private static final RestGraphDatabase gdb = new RestGraphDatabase(neo4jUrl(), getenv("NEO4J_LOGIN"), getenv("NEO4J_PASSWORD"));
    private static final RepositoryService service = new RepositoryService(gdb, new RestCypherQueryEngine(gdb.getRestAPI()));
    private static final String COMMA_SPLIT = "\\s*,\\s*";

    private static String neo4jUrl() {
        final String envUrl = getenv("NEO4J_REST_URL");
        return envUrl != null ? envUrl : "http://localhost:7474/db/data";
    }

    public static void listApps(String query,Map<String,List<String>> categoryNames) {
        renderJSON(service.loadApps(toCategories(categoryNames),query));
    }

    private static Map<String, Category> toCategories(Map<String, List<String>> categoryNames) {
        if (categoryNames==null) return null;
        Map<String, Category> categories=new HashMap<String, Category>();
        for (Map.Entry<String, List<String>> entry : categoryNames.entrySet()) {
            final Category cat = new Category(entry.getKey(),null);
            for (String tag : entry.getValue()) {
                cat.addTag(null, tag, 0);
            }
            categories.put(cat.getName(),cat);
        }
        return categories;
    }

    public static void addApp(Map<String,String> data) {
        final Integer id = service.addApplication(data);
        created("/apps/" + id);
    }

    public static void getApp(Integer id) {
        renderJSON(service.getAppInfo(id));
    }

    public static void updateApp(Integer id,String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
                              String framework, String build, String addOn) {
        try {
            service.updateApplication(id, name, repository, giturl, herokuapp, stack, type, language, framework, build, addOn);
            response.status = Http.StatusCode.NO_RESPONSE;
            location("/apps/" + id);
        } catch(ProcessException pe) {
            notFound();
        }
    }

    private static void location(final String uri) {
        response.setHeader("Location", uri);
    }

    public static void getCategories() {
        renderJSON(service.loadCategories());
    }
    public static void getCategory(String name) {
        renderJSON(service.loadCategories(name).get(name));
    }
    public static void getTag(String name, String tag) {
        final Category category = service.loadCategories(name).get(name);
        if (category==null || category.getTag(tag)==null) {
            notFound();
            return;
        }
        renderJSON(category.getTag(tag));
    }

    public static void addTag(String name, String tag) {
        final Category category = service.loadCategories(name).get(name);
        if (category==null) {
            notFound();
            return;
        }
        final String uri = format("/categories/%s/%s", name, tag);
        if (category.getTag(tag) == null) {
            service.createTagNode(category, tag);
            created(uri);
        } else {
            location(uri);
            ok();
        }
    }

    private static void created(String s) {
        response.status = Http.StatusCode.CREATED;
    }

    public static void getUser(String email) {
        final Node user = service.getUser(email);
        if (user==null) {
            notFound();
        }
        renderJSON(email);
    }
    public static void addUser(String email) {
        final Node user = service.getOrCreateUser(email);
        if (user==null) {
            badRequest();
            return;
        }
        created("/users/"+email);
        renderJSON(email);
    }
}
