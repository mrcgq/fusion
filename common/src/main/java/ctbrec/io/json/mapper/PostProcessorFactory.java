package ctbrec.io.json.mapper;

import ctbrec.io.json.dto.PostProcessorDto;
import ctbrec.recorder.postprocessing.PostProcessor;
import org.mapstruct.ObjectFactory;

public class PostProcessorFactory {

    @SuppressWarnings("unchecked")
    @ObjectFactory
    PostProcessor toPostProcessor(PostProcessorDto dto) {
        try {
            Class<? extends PostProcessor> ppClass = (Class<? extends PostProcessor>) Class.forName(dto.getType());
            return ppClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new MappingException(e);
        }
    }
}
