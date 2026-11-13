package ctbrec.sites.mfc;

import java.util.HashMap;
import java.util.Map;

public class Share {

    private String albums;
    private String follows;
    private String tmAlbum;
    private String things;
    private String clubs;
    private String collections;
    private String stores;
    private String goals;
    private String polls;
    private Map<String, Object> additionalProperties = new HashMap<>();

    public String getAlbums() {
        return albums;
    }

    public void setAlbums(String albums) {
        this.albums = albums;
    }

    public String getFollows() {
        return follows;
    }

    public void setFollows(String follows) {
        this.follows = follows;
    }

    public String getTmAlbum() {
        return tmAlbum;
    }

    public void setTmAlbum(String tmAlbum) {
        this.tmAlbum = tmAlbum;
    }

    public String getThings() {
        return things;
    }

    public void setThings(String things) {
        this.things = things;
    }

    public String getClubs() {
        return clubs;
    }

    public void setClubs(String clubs) {
        this.clubs = clubs;
    }

    public String getCollections() {
        return collections;
    }

    public void setCollections(String collections) {
        this.collections = collections;
    }

    public String getStores() {
        return stores;
    }

    public void setStores(String stores) {
        this.stores = stores;
    }

    public String getGoals() {
        return goals;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }

    public String getPolls() {
        return polls;
    }

    public void setPolls(String polls) {
        this.polls = polls;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public void merge(Share share) {
        if (share == null) {
            return;
        }

        albums = share.albums != null ? share.albums : albums;
        follows = share.follows != null ? share.follows : follows;
        tmAlbum = share.tmAlbum != null ? share.tmAlbum : tmAlbum;
        things = share.things != null ? share.things : things;
        clubs = share.clubs != null ? share.clubs : clubs;
        collections = share.collections != null ? share.collections : collections;
        stores = share.stores != null ? share.stores : stores;
        goals = share.goals != null ? share.goals : goals;
        polls = share.polls != null ? share.polls : polls;
        additionalProperties.putAll(share.additionalProperties);
    }

}
