package ctbrec.io.json.mapper;

import ctbrec.io.json.dto.PostProcessorDto;
import ctbrec.recorder.postprocessing.PostProcessor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = {PostProcessorFactory.class})
public interface PostProcessorMapper {
    @Mapping(target = "type", expression = "java(model.getClass().getName())")
    PostProcessorDto toDto(PostProcessor model);

    PostProcessor toPostProcessor(PostProcessorDto dto) throws MappingException;
}
