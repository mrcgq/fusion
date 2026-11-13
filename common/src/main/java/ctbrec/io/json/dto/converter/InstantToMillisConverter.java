package ctbrec.io.json.dto.converter;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

import java.time.Instant;

public class InstantToMillisConverter implements Converter<Instant, Long> {

    @Override
    public Long convert(Instant instant) {
        return instant.toEpochMilli();
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(Instant.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(long.class);
    }
}
