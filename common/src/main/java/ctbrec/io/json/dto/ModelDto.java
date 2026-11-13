package ctbrec.io.json.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import ctbrec.SubsequentAction;
import ctbrec.io.json.dto.converter.InstantToMillisConverter;
import ctbrec.io.json.dto.converter.MillisToInstantConverter;
import lombok.Data;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
public class ModelDto {
    private String type;
    private String name;
    private String displayName;
    private String description;
    private URI url;
    private URI preview;
    private int priority = -1;
    private int streamUrlIndex = -1;
    private boolean suspended = false;
    private boolean bookmarked = false;
    private boolean forcePriority = false;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant lastSeen;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant lastRecorded;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant addedAt;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant recordUntil;
    private SubsequentAction recordUntilSubsequentAction;
    private Map<String, String> siteSpecific = new HashMap<>();

}
