package controllers;

import com.dmurph.tracking.AnalyticsConfigData;
import com.dmurph.tracking.JGoogleAnalyticsTracker;
import com.google.gson.Gson;
import com.heroku.api.model.App;
import com.heroku.api.request.app.AppInfo;
import domain.Category;
import domain.Tag;
import domain.User;
import helpers.EmailHelper;
import helpers.HerokuAppSharingHelper;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.rest.graphdb.RestAPI;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;
import play.data.validation.Validation;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Http;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static java.lang.System.getenv;
import static java.util.Collections.singletonMap;
import static org.neo4j.helpers.collection.MapUtil.map;

public class Application extends Controller {

    private static final AnalyticsConfigData config = new AnalyticsConfigData("UA-26859570-1");
    private static final RestGraphDatabase gdb = new RestGraphDatabase(neo4jUrl(), getenv("NEO4J_LOGIN"), getenv("NEO4J_PASSWORD"));
    private static final Index<Node> userIndex = gdb.index().forNodes("users");
    private static final Index<Node> appsIndex = gdb.index().forNodes("apps");

    private static String neo4jUrl() {
        final String envUrl = getenv("NEO4J_REST_URL");
        return envUrl!=null ? envUrl : "http://localhost:7474/db/data";
    }

    private static final RestCypherQueryEngine queryEngine = new RestCypherQueryEngine(gdb.getRestAPI());

    public static void index() {
        final Map<String, Category> categories = loadCategories();
        final Collection<AppInfo> apps = loadApps(categories).values();
        render(categories, apps);
    }

    public static void add() {
        Map<String, Category> categories = loadCategories();
        render(categories);
    }

    private static Node getOrCreateUser(String email) {
        if (email==null || email.trim().isEmpty()) return null;
        final Node user = userIndex.get("email", email).getSingle();
        if (user!=null) return user;
        final Node newUser = gdb.getRestAPI().createNode(map("email", email));
        userIndex.add(newUser,"email",email);
        return newUser;
    }
    private static Map<String, Category> loadCategories() {
        final Iterable<Map<String,Object>> result = queryEngine.query("start n=node(0) match n-[:CATEGORY]->category-[?:TAG]->tag<-[?:TAGGED]-() return category, category.category, tag, tag.tag? , count(*) as count", null);
        Map<String,Category> categories=new HashMap<String, Category>();
        for (Map<String, Object> row : result) {
            final String category = row.get("category.category").toString();
            if (!categories.containsKey(category)) categories.put(category, new Category(category,(Node)row.get("category")));
            final Node tagNode = (Node) row.get("tag");
            if (tagNode==null) continue;
            categories.get(category).addTag(tagNode,row.get("tag.tag").toString(),(Integer)row.get("count"));
        }
        return categories;
    }
    
    static class AppInfo {
        Long id;
        String name;
        String url;
        String repository;
        Set<String> tags = new TreeSet<String>();

        AppInfo(Long id, String name, String url, String repository) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.repository = repository;
        }

        public void addTags(Collection<String> tags) {
            this.tags.addAll(tags);
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getRepository() {
            return repository;
        }
    }
    private static Map<Long, AppInfo> loadApps(Map<String, Category> categories) {
        final Iterable<Map<String,Object>> result = queryEngine.query(
                "start app=node:apps('giturl:*') " +
                "match category-[:TAG]->tag<-[:TAGGED]-app-[?:OWNS]->user " +
                "return ID(app) as appid, app.name, app.repository, app.herokuapp, collect(tag.tag) as tags " +
                "order by app.name asc limit 20",null);
        Map<Long, AppInfo> apps = new LinkedHashMap<Long, AppInfo>();
        for (Map<String, Object> row : result) {
            final AppInfo app = createApp(row);
            apps.put(app.getId(),app);
        }
        return apps;
    }

    private static AppInfo createApp(Map<String, Object> row) {
        final String appName = row.get("app.name").toString();
        final Long appId = ((Number)row.get("appid")).longValue();
        final AppInfo app = new AppInfo(appId, appName,row.get("app.herokuapp").toString(),row.get("app.repository").toString());
        final String tagsString = row.get("tags").toString();
        app.addTags(Arrays.asList(tagsString.substring(1,tagsString.length()-1).split(",\\s*")));
        return app;
    }

    public static void addApp(String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
String framework, String build, String addOn, String email) {
        final Node foundApp = appsIndex.get("giturl", giturl).getSingle();
        if (foundApp == null) {
            Node userNode = getOrCreateUser(email);
            final Node appNode = gdb.getRestAPI().createNode(map("name", name, "repository", repository, "giturl", giturl, "herokuapp", herokuapp, "stack", stack));
            appsIndex.add(appNode,"giturl",giturl);
            if (userNode!=null) {
                userNode.createRelationshipTo(appNode,RelTypes.OWNS);
            }
            addNewTags(type, language, framework, build, appNode);
        }
        index();
    }

    private static void addNewTags(String type, String language, String framework, String build, Node appNode) {
        final Map<String, Category> categories = loadCategories();
        addNewTags(categories,"type",type,appNode);
        addNewTags(categories,"language",language,appNode);
        addNewTags(categories,"framework",framework,appNode);
        addNewTags(categories,"build",build,appNode);
    }

    enum RelTypes implements RelationshipType{ TAG, CATEGORY, RATED, TAGGED, OWNS }
    private static void addNewTags(Map<String, Category> categories, String categoryName, String tagString, Node appNode) {
        final Category category = getCategory(categories, categoryName);
        if (category == null) return;

        if (tagString==null || tagString.isEmpty()) return;
        final String[] tags = tagString.split(",\\s*");
        for (String tagName : tags) {
            tagName = tagName.trim();
            if (tagName.isEmpty()) continue;
            final Tag tag = category.getTag(tagName);
            final Node tagNode = tag != null ? tag.getNode() : createTagNode(category, tagName);
            if (tagNode==null) {
                System.err.println("Error loading or creating tag: "+tagName);
                continue;
            }
            appNode.createRelationshipTo(tagNode,RelTypes.TAGGED);
        }
    }

    private static Category getCategory(Map<String, Category> categories, String categoryName) {
        final Category category = categories.get(categoryName);
        if (category==null) {
            System.err.println("Error loading category: "+categoryName);
            return null;
        }
        return category;
    }

    private static Node createTagNode(Category category, String tagName) {
        final Node tagNode = gdb.getRestAPI().createNode(map("tag", tagName));
        category.getNode().createRelationshipTo(tagNode, RelTypes.TAG);
        return tagNode;
    }

    public static void shareApp(String emailAddress, String gitUrl) {
        final Validation.ValidationResult emailValidation = validation.email(emailAddress);
        final Validation.ValidationResult urlValidation = validation.minSize(gitUrl, 1);//todo: convert to matcher

        if (emailValidation.ok && urlValidation.ok) {
            trackShareApp(gitUrl);
            try {
                response.setContentTypeIfNotSet("application/json; charset=" + Http.Response.current().encoding);

                App app = shareAppInBackground(emailAddress, gitUrl);

                response.writeChunk(toJson(singletonMap("result", app)));
            }
            catch (Throwable e) {
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

    private static void renderErrors(Validation.ValidationResult...validationErrors) {
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
