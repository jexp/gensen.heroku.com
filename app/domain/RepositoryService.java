package domain;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
import org.neo4j.rest.graphdb.RestGraphDatabase;
import org.neo4j.rest.graphdb.query.RestCypherQueryEngine;

import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 31.12.11
 */
public class RepositoryService {
    private static final String COMMA_SPLIT = "\\s*,\\s*";
    private static final String GIT_URL = "giturl";
    private static final String REPOSITORY = "repository";
    private static final String HEROKUAPP = "herokuapp";
    private static final String STACK = "stack";
    private static final String NAME = "name";
    private static final String EMAIL = "email";
    private static final String ID = "id";
    private static final String MAX_ID = "max_id";

    private  final RestGraphDatabase gdb;
    private  final RestCypherQueryEngine queryEngine;
    private  final Index<Node> userIndex;
    private  final Index<Node> appsIndex;
    private  final Index<Node> searchIndex;
    

    public RepositoryService(String url, String login, String password) {
        this.gdb = new RestGraphDatabase(url, login, password);
        queryEngine = new RestCypherQueryEngine(gdb.getRestAPI());
        userIndex = gdb.index().forNodes("users");
        appsIndex = gdb.index().forNodes("apps");
        searchIndex = gdb.index().forNodes("search", LuceneIndexImplementation.FULLTEXT_CONFIG);
    }

    public void addApplication(String name, String repository, String giturl, String herokuapp, String stack, String type, String language, String framework, String build, String addOn, String email) {
        final Node foundApp = appsIndex.get(GIT_URL, giturl).getSingle();
        if (foundApp == null) {
            Node userNode = getOrCreateUser(email);
            final Long id = nextId();
            final Node appNode = gdb.getRestAPI().createNode(map(ID, id, NAME, name, REPOSITORY, repository, GIT_URL, giturl, HEROKUAPP, herokuapp, STACK, stack));
            appsIndex.add(appNode,ID,id);
            appsIndex.add(appNode, GIT_URL,giturl);
            searchIndex.add(appNode, NAME,name);
            if (userNode!=null) {
                userNode.createRelationshipTo(appNode,RelTypes.OWNS);
            }
            addNewTags(type, language, framework, build, addOn, appNode);
        }
    }

    public AppInfo getAppInfo(Long id) {
        if (id == null) return null;
        final Map<Long, AppInfo> appInfo = loadApps(null, id);
        if (appInfo==null || appInfo.isEmpty()) return null;
        return appInfo.get(id);
    }

    private Long nextId() {
        Long maxId = (Long) gdb.getReferenceNode().getProperty(MAX_ID,0) + 1;
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

    
    private  Node getOrCreateUser(String email) {
        if (email==null || email.trim().isEmpty()) return null;
        final Node user = userIndex.get(EMAIL, email).getSingle();
        if (user!=null) return user;
        final Node newUser = gdb.getRestAPI().createNode(map(EMAIL, email));
        userIndex.add(newUser, EMAIL,email);
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
                " match category-[:TAG]->tag<-[:TAGGED]-app-[?:OWNS]->user " +
                ((where.isEmpty()) ? "" : " where " + where) +
                " return ID(app) as appid, app.name, app.repository, app.herokuapp, collect(tag.tag) as tags " +
                " order by app.name asc limit 20";
        final Iterable<Map<String,Object>> result = queryEngine.query(
                statement,null);
        System.err.println(statement);
        Map<Long, domain.AppInfo> apps = new LinkedHashMap<Long, AppInfo>();
        for (Map<String, Object> row : result) {
            final domain.AppInfo app = createApp(row);
            apps.put(app.getId(),app);
        }
        return apps;
    }

    private  String loadAppsStart(Object query) {
        if (query instanceof Number) return " start app=node:apps(id={query}) "; 
        if (isQueryString(query)) return " start app=node:search('name:"+query+"') ";
        return " start app=node:apps('id:*') ";
    }

    private boolean isQueryString(Object query) {
        return query instanceof String || !((String)query).trim().isEmpty();
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
        final domain.AppInfo app = new domain.AppInfo(appId, appName,row.get("app.herokuapp").toString(),row.get("app.repository").toString());
        final String tagsString = row.get("tags").toString();
        app.addTags(Arrays.asList(tagsString.substring(1, tagsString.length() - 1).split(COMMA_SPLIT)));
        return app;
    }



    private  void addNewTags(String type, String language, String framework, String build, String addOn, Node appNode) {
        final Map<String, Category> categories = loadCategories();
        addNewTags(categories,"type",type,appNode);
        addNewTags(categories,"language",language,appNode);
        addNewTags(categories,"framework",framework,appNode);
        addNewTags(categories,"build",build,appNode);
        addNewTags(categories,"add-on",addOn,appNode);
    }

    enum RelTypes implements RelationshipType { TAG, CATEGORY, RATED, TAGGED, OWNS }

    private  void addNewTags(Map<String, Category> categories, String categoryName, String tagString, Node appNode) {
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

    private Category getCategory(Map<String, Category> categories, String categoryName) {
        final Category category = categories.get(categoryName);
        if (category==null) {
            System.err.println("Error loading category: "+categoryName);
            return null;
        }
        return category;
    }

    public Node createTagNode(Category category, String tagName) {
        final Node tagNode = gdb.getRestAPI().createNode(map("tag", tagName));
        category.getNode().createRelationshipTo(tagNode, RelTypes.TAG);
        category.addTag(tagNode, tagName, 0);
        return tagNode;
    }

}
