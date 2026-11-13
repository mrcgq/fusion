package ctbrec.io.json.mapper;


import ctbrec.Model;
import ctbrec.io.json.dto.ModelDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = {ModelFactory.class, UriMapper.class})
public interface ModelMapper {

    @Mapping(target = "addedAt", source = "addedTimestamp")
    @Mapping(target = "bookmarked", source = "markedForLaterRecording")
    @Mapping(target = "type", expression = "java(model.getClass().getName())")
    ModelDto toDto(Model model);

    @Mapping(target = "addedTimestamp", source = "addedAt")
    @Mapping(target = "markedForLaterRecording", source = "bookmarked")
    Model toModel(ModelDto dto) throws MappingException;

    @AfterMapping
    default void afterToDto(Model model, @MappingTarget ModelDto dto) {
        model.writeSiteSpecificData(dto.getSiteSpecific());
    }

    @AfterMapping
    default void afterToModel(ModelDto dto, @MappingTarget Model model) {
        model.readSiteSpecificData(dto.getSiteSpecific());
    }
}
