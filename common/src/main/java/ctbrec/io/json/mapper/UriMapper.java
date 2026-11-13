package ctbrec.io.json.mapper;

import org.mapstruct.Mapper;

import java.net.URI;
import java.util.Optional;

@Mapper
public interface UriMapper {
    default URI map(String uri) {
        return Optional.ofNullable(uri).map(URI::create).orElse(null);
    }

    default String map(URI uri) {
        return Optional.ofNullable(uri).map(Object::toString).orElse(null);
    }
}
