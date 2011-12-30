package domain;

import org.neo4j.graphdb.Node;

/**
 * @author mh
 * @since 30.12.11
 */
public class User {
    Node node;
    String email;

    public User(Node node, String email) {
        this.node = node;
        this.email = email;
    }

    public Node getNode() {
        return node;
    }

    public String getEmail() {
        return email;
    }
}
