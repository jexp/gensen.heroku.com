package domain;

import org.neo4j.graphdb.Node;

/**
* @author mh
* @since 30.12.11
*/
public class Tag implements Comparable<Tag> {
    private String name;
    private String icon;
    private int count;
    private transient Node node;

    public Tag(Node node, String name, int count) {
        this.node = node;
        this.name = name;
        this.count = count;
    }

    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
        return name.replaceAll("_", " ");
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
