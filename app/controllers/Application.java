package controllers;

import com.dmurph.tracking.AnalyticsConfigData;
import com.dmurph.tracking.JGoogleAnalyticsTracker;
import com.google.gson.Gson;
import com.heroku.api.model.Addon;
import com.heroku.api.model.App;
import domain.Category;
import domain.Tag;
import helpers.EmailHelper;
import helpers.HerokuApi;
import helpers.HerokuAppSharingHelper;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
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
    private static final Index<Node> searchIndex = gdb.index().forNodes("search", LuceneIndexImplementation.FULLTEXT_CONFIG);
    private static final String COMMA_SPLIT = "\\s*,\\s*";

    private static String neo4jUrl() {
        final String envUrl = getenv("NEO4J_REST_URL");
        return envUrl!=null ? envUrl : "http://localhost:7474/db/data";
    }

    private static final RestCypherQueryEngine queryEngine = new RestCypherQueryEngine(gdb.getRestAPI());

    public static void reindex() {
        final IndexHits<Node> nodes = appsIndex.query("giturl:*");
        System.err.println("found nodes:"+nodes.size());
        response.setContentTypeIfNotSet("application/json; charset=" + Http.Response.current().encoding);
        List<String> names=new ArrayList<String>();
        for (Node node : nodes) {
            searchIndex.remove(node);
            if (node.hasProperty("name")) {
                final String name = (String) node.getProperty("name");
                searchIndex.add(node,"name", name);
                names.add(name);
                System.err.println("added "+name);
            }
        }
        response.writeChunk(toJson(names));
    }

    private static void index() {
        index(null,null);
    }
    public static void index(String tags, String q) {
        final long start = System.currentTimeMillis();
        final Map<String, Category> categories = loadCategories();
        final Collection<domain.AppInfo> apps = loadApps(tags(tags), q).values();
        final long delta = System.currentTimeMillis()-start;
        render(categories, apps,tags,q,delta);
    }

    private static Map<String, Category> tags(String tags) {
        if (tags==null || tags.trim().isEmpty()) return null;
        Map<String, Category> result=new HashMap<String, Category>();
        for (String catTag : tags.trim().split(COMMA_SPLIT)) {
            final String[] split = catTag.split("/");
            final String tagName = split[1];
            final String catName = split[0];
            if (!result.containsKey(catName)) result.put(catName, new Category(catName,null));
            result.get(catName).addTag(null,tagName,0);
        }
        return result;
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


    private static Map<String, Category> loadCategories(String...categoryNames) {
        String where="";
        if (categoryNames.length>0) {
            for (String category : categoryNames) {
                if (where.length()>0) where += " OR ";
                where += "category.category ='"+category+"'";
            }
            where = " where ("+where+") ";
        }
        final Iterable<Map<String,Object>> result = queryEngine.query("start n=node(0) " +
                " match n-[:CATEGORY]->category-[?:TAG]->tag<-[?:TAGGED]-() " +
                where +
                " return category, category.category, tag, tag.tag? , count(*) as count",
                null);
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

    private static Map<Long, domain.AppInfo> loadApps(Map<String, Category> categories, String query) {
        final String where = getWhere(categories);
        final String start = getStart(query);
        final String statement = start +
                " match category-[:TAG]->tag<-[:TAGGED]-app-[?:OWNS]->user " +
                ((where.isEmpty()) ? "" : " where " + where) +
                " return ID(app) as appid, app.name, app.repository, app.herokuapp, collect(tag.tag) as tags " +
                " order by app.name asc limit 20";
        final Iterable<Map<String,Object>> result = queryEngine.query(
                statement,null);
        System.err.println(statement);
        Map<Long, domain.AppInfo> apps = new LinkedHashMap<Long, domain.AppInfo>();
        for (Map<String, Object> row : result) {
            final domain.AppInfo app = createApp(row);
            apps.put(app.getId(),app);
        }
        return apps;
    }

    private static String getStart(String query) {
        return (query==null||query.isEmpty() ? " start app=node:apps('giturl:*') " :
        " start app=node:search('name:"+query+"') ");
    }

    private static String getWhere(Map<String, Category> categories) {
        if (categories==null || categories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Category category : categories.values()) {
            if (sb.length()>0) sb.append(" OR ");
            sb.append("(");
            sb.append("category.category = '").append(category.getName()).append("'").append(" AND (");
            String or = "";
            for (Tag tag : category.getTags()) {
                sb.append(or);
                sb.append("tag.tag ='").append(tag.getName()).append("'");
                or = " OR ";
            }
            sb.append("))");
        }
        return sb.toString();
    }

    private static domain.AppInfo createApp(Map<String, Object> row) {
        final String appName = row.get("app.name").toString();
        final Long appId = ((Number)row.get("appid")).longValue();
        final domain.AppInfo app = new domain.AppInfo(appId, appName,row.get("app.herokuapp").toString(),row.get("app.repository").toString());
        final String tagsString = row.get("tags").toString();
        app.addTags(Arrays.asList(tagsString.substring(1, tagsString.length() - 1).split(COMMA_SPLIT)));
        return app;
    }

    public static void addApp(String name, String repository, String giturl, String herokuapp, String stack, String type, String language,
String framework, String build, String addOn, String email) {
        final Node foundApp = appsIndex.get("giturl", giturl).getSingle();
        if (foundApp == null) {
            Node userNode = getOrCreateUser(email);
            final Node appNode = gdb.getRestAPI().createNode(map("name", name, "repository", repository, "giturl", giturl, "herokuapp", herokuapp, "stack", stack));
            appsIndex.add(appNode,"giturl",giturl);
            searchIndex.add(appNode,"name",name);
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
        final String[] tags = tagString.split(COMMA_SPLIT);
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

    public static void addAddons() {
        final HerokuApi herokuApi = new HerokuApi();
        final Map<String, Category> categories = loadCategories("add-on");
        final Category category = categories.get("add-on");
        final Node categoryNode = category.getNode();
        final List<Addon> addons = herokuApi.listAddons();
        for (Addon addon : addons) {
            String name = addon.getName();
            if (name.contains(":")) name = name.substring(0,name.indexOf(":"));
            if (category.containsTag(name)) continue;
            /*
            final String price = String.format("%.2f USD%s", addon.getPrice_cents() / 100f, addon.getPrice_unit() != null ? "/" + addon.getPrice_unit() : "");
            final Map<String, Object> properties = map("tag", name, "description", addon.getDescription(), "price", price, "state", addon.getState());
            if (addon.getUrl()!=null) {
                properties.put("url",addon.getUrl());
            }
            System.err.println("Adding Addon "+name+" props "+properties);
            final Node addonNode = gdb.getRestAPI().createNode(properties);
            categoryNode.createRelationshipTo(addonNode, RelTypes.TAG);
            category.addTag(addonNode,name,1);
            */
            createTagNode(category,name);
        }
    }

    private static Node createTagNode(Category category, String tagName) {
        final Node tagNode = gdb.getRestAPI().createNode(map("tag", tagName));
        category.getNode().createRelationshipTo(tagNode, RelTypes.TAG);
        category.addTag(tagNode, tagName, 0);
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
