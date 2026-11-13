package ctbrec.recorder.server.io.json.mapper;

import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.io.json.mapper.RecordingMapper;
import ctbrec.recorder.server.Request;
import ctbrec.recorder.server.io.json.dto.RequestDto;
import org.mapstruct.Mapper;

@Mapper(uses = {ModelMapper.class, RecordingMapper.class})
public interface RequestMapper {
    RequestDto toDto(Request recording);

    Request toRequest(RequestDto dto);
}
