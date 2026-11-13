package ctbrec.ui.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {

    @JsonProperty("emojis")
    private List<Object> emojis = null;
    @JsonProperty("note")
    private String note;
    @JsonProperty("bot")
    private Boolean bot;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("avatar")
    private String avatar;
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("header_static")
    private String headerStatic;
    @JsonProperty("url")
    private String url;
    @JsonProperty("following_count")
    private Integer followingCount;
    @JsonProperty("statuses_count")
    private Integer statusesCount;
    @JsonProperty("followers_count")
    private Integer followersCount;
    @JsonProperty("header")
    private String header;
    @JsonProperty("id")
    private String id;
    @JsonProperty("locked")
    private Boolean locked;
    @JsonProperty("avatar_static")
    private String avatarStatic;
    @JsonProperty("fields")
    private List<Object> fields = null;
    @JsonProperty("acct")
    private String acct;
    @JsonProperty("username")
    private String username;
}
