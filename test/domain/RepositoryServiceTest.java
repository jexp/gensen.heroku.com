package domain;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.rest.graphdb.query.QueryEngine;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;

/**
 * @author mh
 * @since 06.01.12
 */
public class RepositoryServiceTest {

    private EmbeddedGraphDatabase gdb;
    private RepositoryService service;
    private Transaction tx;
    private Node language;
    private Node java;
    private Index<Node> appsIndex;
    private Index<Node> searchIndex;

    @Before
    public void setUp() throws Exception {
        FileUtils.deleteDirectory(new File("tmp/db"));
        gdb = new EmbeddedGraphDatabase("tmp/db");
        final QueryEngine<Map<String, Object>> queryEngine = new CypherQueryEngine(gdb);
        tx = gdb.beginTx();
        service = new RepositoryService(gdb, queryEngine);
        language = gdb.createNode();
        language.setProperty(RepositoryService.CATEGORY, "language");
        gdb.getReferenceNode().createRelationshipTo(language, RepositoryService.RelTypes.CATEGORY);
        java = gdb.createNode();
        java.setProperty(RepositoryService.TAG, "java");
        language.createRelationshipTo(java, RepositoryService.RelTypes.TAG);
        appsIndex = gdb.index().forNodes("apps");
        searchIndex = gdb.index().forNodes("search");
    }

    @After
    public void tearDown() throws Exception {
        tx.failure();
        tx.finish();
        gdb.shutdown();
    }

    @Test
    public void testAddApplication() throws Exception {
        addApplication();
        final Node app = appsIndex.get(AppInfo.GIT_URL, "giturl").getSingle();
        assertNotNull(app);
        assertEquals("repository",app.getProperty(AppInfo.REPOSITORY));
        final Integer id = (Integer) app.getProperty(AppInfo.ID);
        System.out.println("id = " + id);
        assertEquals(app,appsIndex.get(AppInfo.ID,id).getSingle());
        assertEquals(app,searchIndex.query(AppInfo.NAME+":nam*").getSingle());
        final Collection<Relationship> tagRels = IteratorUtil.asCollection(app.getRelationships(Direction.OUTGOING, RepositoryService.RelTypes.TAGGED));
        assertEquals(1,tagRels.size());
    }

    private Integer addApplication() {
        return service.addApplication("name","repository","giturl","herokuapp","cedar","demo","java","rails","maven","neo4j","test@test.de", description, docurl, videourl);
    }

    @Test
    public void testGetAppInfo() throws Exception {
        addApplication();
        final Integer id = 1;
        final AppInfo appInfo = service.getAppInfo(id);
        assertEquals("name",appInfo.getName());
        assertEquals(id,appInfo.getId());
        assertEquals("cedar",appInfo.getStack());
        assertEquals(asList("java"), appInfo.getTags());
        final Map<String, Category> categories = appInfo.getCategories();
        assertEquals(1, categories.size());
        final Category category = categories.get("language");
        assertEquals("language", category.getName());
        assertEquals("java", category.getTag("java").getName());
        assertEquals(1, category.getTag("java").getCount());
    }

    @Test
    public void testLoadCategories() throws Exception {
        final Map<String, Category> categories = service.loadCategories();
        assertEquals(1,categories.size());
        final Category category = categories.get("language");
        assertNotNull(category);
        assertEquals(language.getProperty(RepositoryService.CATEGORY), category.getName());
        assertEquals(language,category.getNode());
        assertEquals(asList(java.getProperty(RepositoryService.TAG)),asCollection(category.getTagNames()));
        final Tag tag = category.getTag("java");
        assertEquals(java.getProperty(RepositoryService.TAG), tag.getName());
        assertEquals(java, tag.getNode());
    }

    @Test
    public void testLoadApps() throws Exception {
        addApplication();
        service.addApplication("name2","repository2","giturl2","herokuapp2","cedar","webapp","ruby","sinatra","rake","graphdb","test@test.de", description, docurl, videourl);
        final Map<Integer, AppInfo> apps = service.loadApps(null, "name*");
        assertEquals(2,apps.size());
        assertEquals("ruby", apps.get(2).getCategories().get("language").getTag("ruby").getName());
    }

    @Test
    public void testUpdateApplication() throws Exception {
        addApplication();
        final Integer id = 1;
        service.updateApplication(id,"name1","repository","goturl","herokuapp","bamboo","type","ruby, java","rails","maven","neo4j");
        final Map<Integer, AppInfo> allApps = service.loadApps(null, null);
        assertEquals(1,allApps.size());
        final AppInfo appInfo = service.getAppInfo(id);
        assertEquals("name1", appInfo.getName());
        assertEquals(id,searchIndex.query("name:name1").getSingle().getProperty(AppInfo.ID));
        assertNull(searchIndex.query("name:name").getSingle());
        assertEquals(1, IteratorUtil.count(searchIndex.query("name:nam*").iterator()));
        assertEquals("goturl", appInfo.getGitUrl());
        assertNull(appsIndex.get(AppInfo.GIT_URL, "giturl").getSingle());
        assertEquals(asList("java","ruby"),appInfo.getTags());
    }

    @Test
    public void testCreateTagNode() throws Exception {
        Category category = service.loadCategories("language").get("language");
        assertEquals(asList("java"),IteratorUtil.asCollection(category.getTagNames()));
        service.createTagNode(category, "clojure");
        category = service.loadCategories("language").get("language");
        assertEquals(asList("clojure","java"),IteratorUtil.asCollection(category.getTagNames()));
    }

    @Test
    public void testLike() {
        final Integer appId = addApplication();
        Relationship rel = service.like(appId, "test@test.de", 5, "comment");
        assertRatedRel(rel, 5, "comment");
        final Node node = appsIndex.get(AppInfo.ID, appId).getSingle();
        rel = node.getSingleRelationship(RepositoryService.RelTypes.RATED, Direction.INCOMING);
        assertRatedRel(rel, 5, "comment");
    }
    @Test
    public void testStars() {
        final Integer appId = addApplication();
        service.getOrCreateUser("test2@test.de");
        service.like(appId, "test@test.de", 5, "comment");
        service.like(appId, "test2@test.de", 2, "comment2");

        final Node node = appsIndex.get(AppInfo.ID, appId).getSingle();
        int relCount = IteratorUtil.count(node.getRelationships(RepositoryService.RelTypes.RATED, Direction.INCOMING));
        assertEquals(2, relCount);
        final AppInfo appInfo = service.getAppInfo(appId);
        assertEquals(3.5f,appInfo.getStars(),0f);
    }

    private void assertRatedRel(Relationship rel, final int stars, final String comment) {
        assertEquals(RepositoryService.RelTypes.RATED.name(),rel.getType().name());
        assertEquals(stars,rel.getProperty("stars"));
        assertEquals(comment,rel.getProperty("comment"));
    }
}
