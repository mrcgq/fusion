package ctbrec.io.json.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import ctbrec.Recording;
import ctbrec.io.json.dto.converter.InstantToMillisConverter;
import ctbrec.io.json.dto.converter.MillisToInstantConverter;
import lombok.Data;

import java.io.File;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
public class RecordingDto {
    private String id;
    private ModelDto model;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant startDate;
    private Recording.State status = Recording.State.UNKNOWN;
    private int progress = -1;
    private long sizeInByte = -1;
    private String metaDataFile;
    private boolean singleFile = false;
    private boolean pinned = false;
    private String note;
    private Set<String> associatedFiles = new HashSet<>();
    private File absoluteFile = null;
    private File postProcessedFile = null;
    private int selectedResolution = -1;
}
