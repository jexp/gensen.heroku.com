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
    private final String stack;
    String gitUrl;
    Map<String,Category> categories=new HashMap<String, Category>(); // todo
    
    public AppInfo(Long id, String name, String url, String repository, String stack, String gitUrl) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.repository = repository;
        this.stack = stack;
        this.gitUrl = gitUrl;
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

    public String getStack() {
        return stack;
    }

    public Map<String, Category> getCategories() {
        return categories;
    }

    public List<String> getTags() {
        List<String> result=new ArrayList<String>();
        for (Category category : categories.values()) {
            result.addAll(category.getTagNames());
        }
        return result;
    }

    public void addTag(String categoryName, String tag) {
        if (!categories.containsKey(categoryName)) categories.put(categoryName,new Category(categoryName,null));
        categories.get(categoryName).addTag(null,tag,0);
    }
}
