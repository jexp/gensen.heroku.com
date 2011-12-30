package domain;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author mh
 * @since 30.12.11
 */
public class AppInfo {
    Long id;
    String name;
    String url;
    String repository;
    Set<String> tags = new TreeSet<String>();

    public AppInfo(Long id, String name, String url, String repository) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.repository = repository;
    }

    public void addTags(Collection<String> tags) {
        this.tags.addAll(tags);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getRepository() {
        return repository;
    }
}
