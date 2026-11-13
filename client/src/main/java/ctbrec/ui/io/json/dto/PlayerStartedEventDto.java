package ctbrec.ui.io.json.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.dto.converter.InstantToMillisConverter;
import ctbrec.io.json.dto.converter.MillisToInstantConverter;
import lombok.Data;

import java.time.Instant;

@Data
public class PlayerStartedEventDto {
    private ModelDto model;
    @JsonSerialize(converter = InstantToMillisConverter.class)
    @JsonDeserialize(converter = MillisToInstantConverter.class)
    private Instant timestamp;
}
