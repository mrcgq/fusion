package ctbrec.io.json.mapper;

import ctbrec.Recording;
import ctbrec.io.json.dto.RecordingDto;
import org.mapstruct.Mapper;

@Mapper(uses = ModelMapper.class)
public interface RecordingMapper {

    RecordingDto toDto(Recording recording);

    Recording toRecording(RecordingDto dto);
}
