package ctbrec.io.json.dto.converter;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

import java.time.Instant;

public class MillisToInstantConverter implements Converter<Long, Instant> {
    @Override
    public Instant convert(Long aLong) {
        return Instant.ofEpochMilli(aLong);
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(long.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(Instant.class);
    }
}
