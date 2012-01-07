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
    static final String GIT_URL = "giturl";
    static final String REPOSITORY = "repository";
    private static final String HEROKUAPP = "herokuapp";
    private static final String STACK = "stack";
    static final String NAME = "name";
    private static final String EMAIL = "email";
    static final String ID = "id";
    private static final String MAX_ID = "max_id";

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

    public Long addApplication(String name, String repository, String giturl, String herokuapp, String stack, String type, String language, String framework, String build, String addOn, String email) {
        final Node foundApp = appsIndex.get(GIT_URL, giturl).getSingle();
        if (foundApp != null) return (Long)foundApp.getProperty(ID);

        Node userNode = getOrCreateUser(email);
        final Long id = nextId();
        final Node appNode = gdb.createNode();
        updateProperty(appNode,ID, id,appsIndex);
        if (userNode!=null) {
            userNode.createRelationshipTo(appNode,RelTypes.OWNS);
        }
        appsIndex.add(appNode,ID,id);
        update(appNode, name, repository, giturl, herokuapp, stack);
        addNewTags(appNode, type, language, framework, build, addOn);
        return id;
    }

    private void update(Node appNode, String name, String repository, String gitUrl, String herokuApp, String stack) {
        updateProperty(appNode,NAME, name,searchIndex);
        updateProperty(appNode,GIT_URL, gitUrl,appsIndex);
        updateProperty(appNode, REPOSITORY, repository,null);
        updateProperty(appNode, HEROKUAPP, herokuApp,null);
        updateProperty(appNode, STACK, stack,null);
    }

    private void updateProperty(Node node, String prop, Object value, Index<Node> index) {
        final Object existing = node.getProperty(prop, null);
        if (existing!=null && existing.equals(value)) return;
        node.setProperty(prop,value);
        if (index==null) return;
        if (existing!=null) index.remove(node,prop,existing);
        index.add(node,prop,value);
    }

    public AppInfo getAppInfo(Long id) {
        if (id == null) return null;
        final Map<Long, AppInfo> appInfo = loadApps(null, id);
        if (appInfo==null || appInfo.isEmpty()) return null;
        return appInfo.get(id);
    }

    private Long nextId() {
        Long maxId = (Long) gdb.getReferenceNode().getProperty(MAX_ID,0L) + 1;
        gdb.getReferenceNode().setProperty(MAX_ID,maxId);
        return maxId;
    }

    public List<String> reindexApps() {
        final IndexHits<Node> nodes = appsIndex.query(GIT_URL+":*");
        List<String> names=new ArrayList<String>();
        long maxId=-1;
        List<Node> nodesWithoutId = new ArrayList<Node>();
        for (Node node : nodes) {
            searchIndex.remove(node);
            if (node.hasProperty(NAME)) {
                final String name = (String) node.getProperty(NAME);
                searchIndex.add(node, NAME, name);
                names.add(name);
                System.err.println("added "+name);
            }
            if (node.hasProperty(ID)) {
                Long id= (Long) node.getProperty(ID);
                maxId = Math.max(maxId,id);
            } else {
                nodesWithoutId.add(node);
            }
        }
        long id = maxId+1;
        for (Node node : nodesWithoutId) {
            node.setProperty(ID,id);
            appsIndex.remove(node, ID, id);
            appsIndex.add(node, ID,id);
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

    public Map<Long, domain.AppInfo> loadApps(Map<String, Category> categories, Object query) {
        final String where = loadAppsWhere(categories);
        final String start = loadAppsStart(query);
        final String statement = start +
                " match p = category-[:TAG]->tag, tag<-[:TAGGED]-app-[?:OWNS]->user " +
                ((where.isEmpty()) ? "" : " where " + where) +
                " return app.id as appid, app.name, app.giturl, app.stack, app.repository, app.herokuapp, " +
                " collect(extract(n in NODES(p) : coalesce(n.tag?,n.category?))) as tags " +
                " order by app.name asc limit 20";
        final Iterable<Map<String,Object>> result = queryEngine.query(
                statement,null); // map("query",query.toString())
        System.err.println(statement);
        Map<Long, domain.AppInfo> apps = new LinkedHashMap<Long, AppInfo>();
        for (Map<String, Object> row : result) {
            final domain.AppInfo app = createApp(row);
            apps.put(app.getId(),app);
        }
        return apps;
    }

    private  String loadAppsStart(Object query) {
        if (query instanceof Number) return " start app=node:apps(id='"+query+"') ";
        if (isQueryString(query)) return " start app=node:search('name:"+query+"') ";
        return " start app=node:apps('id:*') ";
    }

    private boolean isQueryString(Object query) {
        return query instanceof String && !((String)query).trim().isEmpty();
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

    private  domain.AppInfo createApp(Map<String, Object> row) {
        final String appName = row.get("app.name").toString();
        final Long appId = ((Number)row.get("appid")).longValue();
        final domain.AppInfo app = new domain.AppInfo(appId, appName,row.get("app.herokuapp").toString(),row.get("app.repository").toString(),row.get("app.stack").toString(),row.get("app.giturl").toString());
        addTags(row, app);
        return app;
    }

    private void addTags(Map<String, Object> row, AppInfo app) {
        final String tagsString = row.get("tags").toString();
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
        addNewTags(categories,"add-on",addOn,appNode, existingTags);
        for (Relationship relationship : existingTags.values()) {
            relationship.delete();
        }
    }

    public void updateApplication(Long id, String name, String repository, String giturl, String herokuapp, String stack, String type, String language, String framework, String build, String addOn) {
        final Node app = appsIndex.get(ID, id).getSingle();
        if (app ==null) throw new ProcessException("Application with id "+id+" not found");
        update(app,name,repository,giturl, herokuapp,stack);
        addNewTags(app, type, language, framework, build, addOn);
    }

    public Long addApplication(Map<String, String> data) {
        return addApplication(data.get(NAME),data.get(REPOSITORY),data.get(GIT_URL),data.get(HEROKUAPP),data.get(STACK),data.get("type"),data.get("language"),data.get("framework"),data.get("build"),data.get("add-on"),data.get("email"));
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
        final QueryResult<Map<String,Object>> result = queryEngine.query("start n=node({" + ID + "}) match n-[r:TAGGED]->tag return tag,r", map(ID, appNode.getId()));
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
