package domain;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.rest.graphdb.query.QueryEngine;
import org.neo4j.rest.graphdb.util.QueryResult;
import org.neo4j.rest.graphdb.util.QueryResultBuilder;

import java.util.Collections;
import java.util.Map;

/**
* @author mh
* @since 06.01.12
*/
public class CypherQueryEngine implements QueryEngine<Map<String, Object>> {

    private final GraphDatabaseService gdb;

    CypherQueryEngine(GraphDatabaseService gdb) {
        this.gdb = gdb;
    }

    @Override
    public QueryResult<Map<String, Object>> query(String query, Map<String, Object> params) {
        final ExecutionResult result = new ExecutionEngine(gdb).execute(query, params==null ? Collections.<String,Object>emptyMap() : params);
        return new QueryResultBuilder<Map<String, Object>>(result);
    }
}
