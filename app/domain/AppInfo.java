package domain;

import com.google.gson.Gson;
import org.neo4j.graphdb.Node;

import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 30.12.11
 */
public class AppInfo {
    public static final String REPOSITORY = "repository";
    public static final String HEROKUAPP = "herokuapp";
    public static final String STACK = "stack";
    public static final String DESCRIPTION = "description";
    public static final String DOCURL = "docurl";
    public static final String VIDEOURL = "videourl";
    public static final String NAME = "name";
    public static final String ID = "id";
    public static final String GIT_URL = "giturl";
    private String owner;
    private Map<String,Category> categories=new HashMap<String, Category>(); // todo
    private float stars = 0f;
    private final Map<String, Object> props;
    private final Collection<Rating> ratings = new ArrayList<Rating>();

    public AppInfo(Integer id, String name, String url, String repository, String stack, String gitUrl) {
        this(map(ID,id,NAME,name,HEROKUAPP,url,REPOSITORY,repository,STACK,stack,GIT_URL,gitUrl));
    }

    public AppInfo(Node app) {
        this(props(app, ID, NAME, REPOSITORY, STACK, GIT_URL, VIDEOURL, DOCURL, DESCRIPTION ));
    }

    public AppInfo(Map<String, Object> props) {
        this.props = props;
    }

    private static Map<String, Object> props(Node app, String...names) {
        final HashMap<String, Object> props = new HashMap<String, Object>();
        for (String name : names) {
            props.put(name, app.getProperty(name,null));
        }
        return props;
    }

    public Integer getId() {
        return (Integer) props.get(ID);
    }

    public String getName() {
        return (String) props.get(NAME);
    }

    public String getUrl() {
        return (String) props.get(HEROKUAPP);
    }

    public String getRepository() {
        return (String) props.get(NAME);
    }

    public String getGitUrl() {
        return (String) props.get(GIT_URL);
    }

    public String getStack() {
        return (String) props.get(STACK);
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
    public String getStarsPercent() {
        return String.format("%d%%",(int)(stars*20));
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


    public String getDescription() {
        return (String) props.get(DESCRIPTION);
    }

    public String getVideourl() {
        return (String) props.get(VIDEOURL);
    }

    public String getDocurl() {
        return (String) props.get(DOCURL);
    }

    public String getJson() {
        return new Gson().toJson(this);
    }

    public void addRating(int stars, String comment, String rater) {
        this.ratings.add(new Rating(stars,comment,rater));
    }

    public void calculateStars() {
        float sum = 0f;
        for (Rating rating : ratings) {
            sum += rating.getStars();
        }
        this.stars = ratings.isEmpty() ? 0f : sum / ratings.size();
    }

    public Rating getRating(String email) {
        for (Rating rating : ratings) {
            if (rating.getRater().equals(email)) return rating;
        }
        return null;
    }
    static class Rating {

        private final int stars;
        private final String comment;
        private final String rater;

        public Rating(int stars, String comment, String rater) {
            this.stars = stars;
            this.comment = comment;
            this.rater = rater;
        }

        public int getStars() {
            return stars;
        }

        public String getComment() {
            return comment;
        }

        public String getRater() {
            return rater;
        }

        public String getStarsPercent() {
            return String.format("%d%%", (int) (stars * 20));
        }

    }

    public Collection<Rating> getRatings() {
        return ratings;
    }
}
