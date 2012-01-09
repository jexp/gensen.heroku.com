package domain;

import java.util.*;

/**
 * @author mh
 * @since 30.12.11
 */
public class AppInfo {
    private final Integer id;
    private final String name;
    private final String url;
    private final String repository;
    private final String stack;
    private final String gitUrl;
    private String owner;
    private Map<String,Category> categories=new HashMap<String, Category>(); // todo
    private float stars = 0f;

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

    public List<String> getCategoryTags() {
        List<String> result=new ArrayList<String>();
        for (Category category : categories.values()) {
            final String categoryName = category.getName();
            for (String tagName : category.getTagNames()) {
                result.add(String.format("%s/%s", categoryName, tagName));
            }
        }
        return result;
    }

    public void addTag(String categoryName, String tag) {
        if (!categories.containsKey(categoryName)) categories.put(categoryName,new Category(categoryName,null));
        categories.get(categoryName).addTag(null,tag,1);
    }

    public float getStars() {
        return stars;
    }

    public void setStars(float stars) {
        if (stars<0 || stars > 5 || Float.isNaN(stars)) {
            this.stars=0;
        }
        else {
            this.stars = stars;
        }
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
    public boolean isOwner(String user) {
        return this.owner!=null && user!=null && user.equals(owner);
    }
}
