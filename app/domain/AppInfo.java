package domain;

import java.util.*;

/**
 * @author mh
 * @since 30.12.11
 */
public class AppInfo {
    Long id;
    String name;
    String url;
    String repository;
    String gitUrl;
    Set<String> tags = new TreeSet<String>();
    List<Category> categories=new ArrayList<Category>(); // todo
    
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

    public String getGitUrl() {
        return gitUrl;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public Set<String> getTags() {
        return tags;
    }
}
