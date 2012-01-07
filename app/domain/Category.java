package domain;

import org.neo4j.graphdb.Node;

import java.util.*;

/**
* @author mh
* @since 30.12.11
*/
public class Category {
    private String name;
    private Map<String,Tag> tags=new TreeMap<String,Tag>();
    private transient Node node;

    public Category(String name, Node node) {
        this.name = name;
        this.node = node;
    }
    public void addTag(Node node, String name, int count) {
        tags.put(name,new Tag(node,name,count));
    }

    public String getName() {
        return name;
    }

    public Collection<Tag> getTags() {
        return tags.values();
    }

    public boolean containsTag(String tagName) {
        return tags.containsKey(tagName);
    }

    public Node getNode() {
        return node;
    }

    public Tag getTag(String tagName) {
        return tags.get(tagName);
    }

    public Collection<String> getTagNames() {
        return tags.keySet();
    }
}
