package ctbrec.io.json.mapper;

import ctbrec.Model;
import ctbrec.io.json.dto.ModelDto;
import org.mapstruct.ObjectFactory;

public class ModelFactory {

    @SuppressWarnings("unchecked")
    @ObjectFactory
    Model toModel(ModelDto dto) {
        try {
            Class<? extends Model> modelClass = (Class<? extends Model>) Class.forName(dto.getType());
            return modelClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new MappingException(e);
        }
    }
}
