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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    }

    @Test
    public void testAddApplication() throws Exception {
        service.addApplication("name","repository","giturl","herokuapp","cedar","demo","java","rails","maven","neo4j","test@test.de");
        final Node app = appsIndex.get(RepositoryService.GIT_URL, "giturl").getSingle();
        assertNotNull(app);
        assertEquals("repository",app.getProperty(RepositoryService.REPOSITORY));
        final Long id = (Long) app.getProperty(RepositoryService.ID);
        assertEquals(app,appsIndex.get(RepositoryService.ID,id).getSingle());
        assertEquals(app,searchIndex.query(RepositoryService.NAME+":nam*").getSingle());
        final Collection<Relationship> tagRels = IteratorUtil.asCollection(app.getRelationships(Direction.OUTGOING, RepositoryService.RelTypes.TAGGED));
        assertEquals(1,tagRels.size());
        final AppInfo appInfo = service.getAppInfo(id);
        assertEquals("name",appInfo.getName());
        assertEquals(id,appInfo.getId());
        assertEquals("cedar",appInfo.getStack());
    }

    @Test
    public void testGetAppInfo() throws Exception {

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

    }

    @Test
    public void testUpdateApplication() throws Exception {

    }

    @Test
    public void testCreateTagNode() throws Exception {

    }

}
