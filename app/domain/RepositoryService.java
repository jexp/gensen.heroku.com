package domain;

import helpers.ProcessException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
import org.neo4j.rest.graphdb.query.QueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;

import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 31.12.11
 */
public class RepositoryService {
    private static final String COMMA_SPLIT = "\\s*,\\s*";
    private static final String EMAIL = "email";
    public static final String MAX_ID = "max_id";

    private  final GraphDatabaseService gdb;
    private  final QueryEngine queryEngine;
    private  final Index<Node> userIndex;
    private  final Index<Node> appsIndex;
    private  final Index<Node> searchIndex;
    public static final String CATEGORY = "category";
    public static final String TAG = "tag";


    public RepositoryService(final GraphDatabaseService graphDatabase, final QueryEngine queryEngine) {
        this.gdb = graphDatabase;
        this.queryEngine = queryEngine;
        userIndex = gdb.index().forNodes("users");
        appsIndex = gdb.index().forNodes("apps");
        searchIndex = gdb.index().forNodes("search", LuceneIndexImplementation.FULLTEXT_CONFIG);
    }

    public Integer addApplication(String name, String repository, String giturl, String herokuapp, String stack, String type, String language, String framework, String build, String addOn, String email, String description, String docurl, String videourl) {
        final Node foundApp = appsIndex.get(AppInfo.GIT_URL, giturl).getSingle();
        if (foundApp != null) return intValue(foundApp.getProperty(AppInfo.ID));

        Node userNode = getOrCreateUser(email);
        final Integer id = nextId();
        final Node appNode = gdb.createNode();
        updateProperty(appNode, AppInfo.ID, id,appsIndex);
        if (userNode!=null) {
            userNode.createRelationshipTo(appNode,RelTypes.OWNS);
        }
        appsIndex.add(appNode, AppInfo.ID,id);
        update(appNode, name, repository, giturl, herokuapp, stack,description,docurl,videourl);
        addNewTags(appNode, type, language, framework, build, addOn);
        return id;
    }

    private void update(Node appNode, String name, String repository, String gitUrl, String herokuApp, String stack, String description, String docurl, String videourl) {
        updateProperty(appNode, AppInfo.NAME, name,searchIndex);
        updateProperty(appNode, AppInfo.GIT_URL, gitUrl,appsIndex);
        updateProperty(appNode, AppInfo.REPOSITORY, repository,null);
        updateProperty(appNode, AppInfo.HEROKUAPP, herokuApp,null);
        updateProperty(appNode, AppInfo.STACK, stack,null);
        updateProperty(appNode, AppInfo.DOCURL, docurl,null);
        updateProperty(appNode, AppInfo.DESCRIPTION, description,null);
        updateProperty(appNode, AppInfo.VIDEOURL, videourl,null);
    }

    private void updateProperty(Node node, String prop, Object value, Index<Node> index) {
        final Object existing = node.getProperty(prop, null);
        if (existing!=null && existing.equals(value)) return;
        if (value==null) {
            node.removeProperty(prop);
        } else {
            node.setProperty(prop,value);
        }
        if (index==null) return;
        if (existing!=null) index.remove(node,prop,existing);
        if (value!=null) index.add(node,prop,value);
    }

    public AppInfo getAppInfo(Integer id) {
        if (id == null) return null;
        return loadApp(id);
    }

    private Integer nextId() {
        Integer maxId = intValue(gdb.getReferenceNode().getProperty(MAX_ID, 0L)) + 1;
        gdb.getReferenceNode().setProperty(MAX_ID,maxId);
        return maxId;
    }

    public List<String> reindexApps() {
        final IndexHits<Node> nodes = appsIndex.query(AppInfo.GIT_URL+":*");
        List<String> names=new ArrayList<String>();
        int maxId=-1;
        List<Node> nodesWithoutId = new ArrayList<Node>();
        for (Node node : nodes) {
            searchIndex.remove(node);
            if (node.hasProperty(AppInfo.NAME)) {
                final String name = (String) node.getProperty(AppInfo.NAME);
                searchIndex.add(node, AppInfo.NAME, name);
                names.add(name);
                System.err.println("added "+name);
            }
            if (node.hasProperty(AppInfo.ID)) {
                Integer id = intValue(node.getProperty(AppInfo.ID));
                maxId = Math.max(maxId,id);
            } else {
                nodesWithoutId.add(node);
            }
        }
        int id = maxId+1;
        for (Node node : nodesWithoutId) {
            node.setProperty(AppInfo.ID,id);
            appsIndex.remove(node, AppInfo.ID, id);
            appsIndex.add(node, AppInfo.ID,id);
            id++;
        }
        gdb.getReferenceNode().setProperty(MAX_ID,id);
        return names;
    }

    public  Node getUser(String email) {
        if (email==null || email.trim().isEmpty()) return null;
        return userIndex.get(EMAIL, email).getSingle();
    }

    public  Node getOrCreateUser(String email) {
        if (email==null || email.trim().isEmpty()) return null;
        final Node user = userIndex.get(EMAIL, email).getSingle();
        if (user!=null) return user;
        final Node newUser = gdb.createNode();
        updateProperty(newUser, EMAIL, email, userIndex);
        return newUser;
    }


    public Map<String, Category> loadCategories(String... categoryNames) {
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
                " return category, category.category, tag, tag.tag? as tagName, tag.icon? as icon, count(*) as count",
                Collections.EMPTY_MAP);
        Map<String,Category> categories=new HashMap<String, Category>();
        for (Map<String, Object> row : result) {
            final String category = string(row, "category.category");
            if (!categories.containsKey(category)) categories.put(category, new Category(category,(Node)row.get("category")));
            final Node tagNode = (Node) row.get("tag");
            if (tagNode==null) continue;
            final Tag tag = categories.get(category).addTag(tagNode, string(row, "tagName"), (Integer) row.get("count"));
            final Object icon = row.get("icon");
            if (icon != null)  {
                tag.setIcon(icon.toString());
            }
        }
        return categories;
    }

    public Relationship like(Integer appid, String email, int stars, String comment) {
        getOrCreateUser(email);
        final QueryResult<Map<String,Object>> result = queryEngine.query("start user=node:users(email={email}), app=node:apps(id={id}) match user-[r?:RATED]->app return user,r,app", map("email", email, AppInfo.ID, appid));
        for (Map<String, Object> row : result) {
            Relationship rel = (Relationship) row.get("r");
            if (rel ==null) {
                Node app= (Node) row.get("app");
                Node user= (Node) row.get("user");
                rel = user.createRelationshipTo(app, RelTypes.RATED);
            }
            rel.setProperty("stars",stars);
            rel.setProperty("comment",comment);
            return rel;
        }
        return null;
    }

    enum AppQueryType {
        ALL {
            public String queryFor(Object param) {
                return " start app=node:apps('id:*') ";
            }
        }, USER {
            public String queryFor(Object param) {
                return " start user=node:users(email='"+param+"') ";
            }

            public String userRelationship() {
                return "";
            }
        }, SEARCH {
            public String queryFor(Object param) {
                return " start app=node:search('name:*"+param+"*') ";
            }
        }, ID {
            public String queryFor(Object param) {
                return " start app=node:apps(id='"+param+"') ";
            }
        };

        public abstract String queryFor(Object param);
        public static AppQueryType forQuery(Object param) {
            if (param instanceof Number) return ID;
            if (isQueryString(param)) {
                if (param.toString().contains("@")) {
                    return USER;
                }
                return SEARCH;
            }
            return ALL;
        }
        public String userRelationship() {
            return "?";
        }
        private static boolean isQueryString(Object query) {
                return query instanceof String && !((String)query).trim().isEmpty();
            }

    }
    public Map<Integer, AppInfo> loadApps(Map<String, Category> categories, Object query) {
        final AppQueryType queryType = AppQueryType.forQuery(query);
        final String where = loadAppsWhere(categories);
        final String start = queryType.queryFor(query);
        final String statement = start +
                " match p = category-[:TAG]->tag, tag<-[:TAGGED]-app<-  ["+queryType.userRelationship()+":OWNS]-user,app<-[r?:RATED]-() " +
                ((where.isEmpty()) ? "" : " where " + where) +
                " return app, user.email? as owner," +
                " collect(extract(n in NODES(p) : coalesce(n.tag?,n.category?))) as tags, avg(r.stars?) as rating " +
                " order by app.name asc limit 20";
        final Iterable<Map<String,Object>> result = queryEngine.query(
                statement,null); // map("query",query.toString())
        System.err.println(statement);
        Map<Integer, domain.AppInfo> apps = new LinkedHashMap<Integer, AppInfo>();
        for (Map<String, Object> row : result) {
            final domain.AppInfo app = createApp(row);
            apps.put(app.getId(),app);
        }
        return apps;
    }
    private  String loadAppsWhere(Map<String, Category> categories) {
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

    public AppInfo loadApp(Integer id) {
        final String statement = " start app=node:apps(id='"+ id +"') " +
                " match p = category-[:TAG]->tag, tag<-[:TAGGED]-app<-[:OWNS]-user,app<-[r?:RATED]-rated " +
                " return app.id as appid, app.name, app, user.email? as owner," +
                " collect(extract(n in NODES(p) : coalesce(n.tag?,n.category?))) as tags, r.stars? as stars, r.comment? as comment, rated.email? as rater";
        final Iterable<Map<String,Object>> result = queryEngine.query(
                statement,null); // map("query",query.toString())
        System.err.println(statement);
        domain.AppInfo app=null;
        for (Map<String, Object> row : result) {
            if (app==null) app = createApp(row);
            if (row.get("stars")!=null) { app.addRating(intValue(row.get("stars")),string(row,"comment"),string(row,"rater")); }
        }
        app.calculateStars();
        return app;
    }


    private  domain.AppInfo createApp(Map<String, Object> row) {
        final Node appNode = (Node)row.get("app");
        final domain.AppInfo app = new domain.AppInfo(appNode);
        app.setStars(floatValue(row,"rating"));
        app.setOwner(string(row,"owner"));
        addTags(row, app);
        return app;
    }

    private float floatValue(Map<String, Object> row, String name) {
        final Object value = row.get(name);
        if (value == null) return 0f;
        return Float.parseFloat(value.toString());
    }

    private String string(Map<String, Object> row, String column) {
        final Object value = row.get(column);
        return value!=null ? value.toString() : null;
    }

    private int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private void addTags(Map<String, Object> row, AppInfo app) {
        final String tagsString = string(row, "tags");
        for (String tagPath : tagsString.substring(1, tagsString.length() - 2).split("(\\), )?List\\(")) {
            if (tagPath.isEmpty()) continue;
            final String[] pair = tagPath.split(COMMA_SPLIT);
            app.addTag(pair[0],pair[1]);
        }
    }


    private  void addNewTags(Node appNode, String type, String language, String framework, String build, String addOn) {
        final Map<String, Category> categories = loadCategories();
        final Map<Node, Relationship> existingTags = loadExistingTags(appNode);
        addNewTags(categories,"type",type,appNode,existingTags);
        addNewTags(categories,"language",language,appNode, existingTags);
        addNewTags(categories,"framework",framework,appNode, existingTags);
        addNewTags(categories,"build",build,appNode, existingTags);
        addNewTags(categories,"addon",addOn,appNode, existingTags);
        for (Relationship relationship : existingTags.values()) {
            relationship.delete();
        }
    }

    public void updateApplication(Integer id, String name, String repository, String giturl, String herokuapp, String stack, String type, String language, String framework, String build, String addon, String description,String docurl, String videourl) {
        final Node app = appsIndex.get(AppInfo.ID, id).getSingle();
        if (app ==null) throw new ProcessException("Application with id "+id+" not found");
        update(app,name,repository,giturl, herokuapp,stack, description, docurl, videourl);
        addNewTags(app, type, language, framework, build, addon);
    }

    public Integer addApplication(Map<String, String> data) {
        return addApplication(data.get(AppInfo.NAME),data.get(AppInfo.REPOSITORY),data.get(AppInfo.GIT_URL),data.get(AppInfo.HEROKUAPP),data.get(AppInfo.STACK),data.get("type"),data.get("language"),data.get("framework"),data.get("build"),data.get("addon"),data.get("email"),  data.get(AppInfo.DESCRIPTION),data.get(AppInfo.DOCURL),data.get(AppInfo.VIDEOURL));
    }

    public void updateApplication(Integer id, Map<String, String> data) {
        updateApplication(id, data.get(AppInfo.NAME),data.get(AppInfo.REPOSITORY),data.get(AppInfo.GIT_URL),data.get(AppInfo.HEROKUAPP),data.get(AppInfo.STACK),data.get("type"),data.get("language"),data.get("framework"),data.get("build"),data.get("addon"), data.get(AppInfo.DESCRIPTION),data.get(AppInfo.DOCURL),data.get(AppInfo.VIDEOURL));
    }

    public enum RelTypes implements RelationshipType { TAG, CATEGORY, RATED, TAGGED, OWNS }

    private  void addNewTags(Map<String, Category> categories, String categoryName, String tagString, Node appNode, Map<Node, Relationship> existingTags) {
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
            if (existingTags.remove(tagNode)==null) {
                appNode.createRelationshipTo(tagNode,RelTypes.TAGGED);
            }
        }
    }

    Map<Node, Relationship> loadExistingTags(Node appNode) {
        final Map<Node,Relationship> existingTags = new HashMap<Node, Relationship>();
        final QueryResult<Map<String,Object>> result = queryEngine.query("start n=node({" + AppInfo.ID + "}) match n-[r:TAGGED]->tag return tag,r", map(AppInfo.ID, appNode.getId()));
        for (Map<String, Object> row : result) {
            existingTags.put((Node)row.get("tag"), (Relationship)row.get("r"));
        }
        return existingTags;
    }

    private Category getCategory(Map<String, Category> categories, String categoryName) {
        final Category category = categories.get(categoryName);
        if (category==null) {
            System.err.println("Error loading category: "+categoryName);
            return null;
        }
        return category;
    }

    public Node createTagNode(Category category, String tagName) {
        final Node tagNode = gdb.createNode();
        updateProperty(tagNode, "tag", tagName,null);
        category.getNode().createRelationshipTo(tagNode, RelTypes.TAG);
        category.addTag(tagNode, tagName, 0);
        return tagNode;
    }

}
