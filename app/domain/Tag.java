package domain;

import controllers.Application;
import org.neo4j.graphdb.Node;

/**
* @author mh
* @since 30.12.11
*/
public class Tag implements Comparable<Tag> {
    String name;
    int count;
    private Node node;

    public Tag(Node node, String name, int count) {
        this.node = node;
        this.name = name;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public int compareTo(Tag o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    public Node getNode() {
        return node;
    }
}