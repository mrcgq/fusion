package ctbrec.ui.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {

    @JsonProperty("pinned")
    private Boolean pinned;
    @JsonProperty("in_reply_to_id")
    private Object inReplyToId;
    @JsonProperty("favourites_count")
    private Integer favouritesCount;
    @JsonProperty("media_attachments")
    private List<Object> mediaAttachments = null;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("replies_count")
    private Integer repliesCount;
    @JsonProperty("language")
    private String language;
    @JsonProperty("in_reply_to_account_id")
    private Object inReplyToAccountId;
    @JsonProperty("content")
    private String content;
    @JsonProperty("reblog")
    private Object reblog;
    @JsonProperty("spoiler_text")
    private String spoilerText;
    @JsonProperty("id")
    private String id;
    @JsonProperty("reblogged")
    private Boolean reblogged;
    @JsonProperty("muted")
    private Boolean muted;
    @JsonProperty("emojis")
    private List<Object> emojis = null;
    @JsonProperty("reblogs_count")
    private Integer reblogsCount;
    @JsonProperty("visibility")
    private String visibility;
    @JsonProperty("sensitive")
    private Boolean sensitive;
    @JsonProperty("uri")
    private String uri;
    @JsonProperty("url")
    private String url;
    @JsonProperty("tags")
    private List<Object> tags = null;
    @JsonProperty("application")
    private Object application;
    @JsonProperty("favourited")
    private Boolean favourited;
    @JsonProperty("mentions")
    private List<Object> mentions = null;
    @JsonProperty("account")
    private Account account;
    @JsonProperty("card")
    private Object card;

    public ZonedDateTime getCreationTime() {
        String timestamp = getCreatedAt();
        var instant = Instant.parse(timestamp);
        var time = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        return time;
    }
}
