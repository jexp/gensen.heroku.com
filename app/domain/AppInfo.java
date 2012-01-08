package domain;

import java.util.*;

/**
 * @author mh
 * @since 30.12.11
 */
public class AppInfo {
    Integer id;
    String name;
    String url;
    String repository;
    private final String stack;
    String gitUrl;
    Map<String,Category> categories=new HashMap<String, Category>(); // todo
    
    public AppInfo(Integer id, String name, String url, String repository, String stack, String gitUrl) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.repository = repository;
        this.stack = stack;
        this.gitUrl = gitUrl;
    }

    public Integer getId() {
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


    public Collection<String> getTags(String categoryName) {
        final Category category = categories.get(categoryName);
        return category!=null ? category.getTagNames() : Collections.<String>emptyList();
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
        categories.get(categoryName).addTag(null,tag,1);
    }
}
