package ctbrec.ui.io.json.mapper;

import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.ui.event.PlayerStartedEvent;
import ctbrec.ui.io.json.dto.PlayerStartedEventDto;
import org.mapstruct.Mapper;

@Mapper(uses = ModelMapper.class)
public interface PlayerStartedEventMapper {

    PlayerStartedEventDto toDto(PlayerStartedEvent event);

    PlayerStartedEvent toEvent(PlayerStartedEventDto dto);
}
