package ctbrec.io.json.mapper;

import ctbrec.io.json.dto.CookieDto;
import okhttp3.Cookie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;

@Mapper
public interface CookieMapper {
    default Cookie toCookie(CookieDto dto) {
        var builder = new Cookie.Builder()
                .name(dto.getName())
                .domain(dto.getDomain())
                .path(dto.getPath())
                .value(dto.getValue())
                .expiresAt(dto.getExpiresAt());
        if (dto.isHostOnly()) builder.hostOnlyDomain(dto.getDomain());
        if (dto.isHttpOnly()) builder.httpOnly();
        if (dto.isSecure()) builder.secure();
        return builder.build();
    }

    @Mapping(target = "name", expression = "java(cookie.name())")
    @Mapping(target = "domain", expression = "java(cookie.domain())")
    @Mapping(target = "path", expression = "java(cookie.path())")
    @Mapping(target = "value", expression = "java(cookie.value())")
    @Mapping(target = "expiresAt", expression = "java(cookie.expiresAt())")
    @Mapping(target = "hostOnly", expression = "java(cookie.hostOnly())")
    @Mapping(target = "httpOnly", expression = "java(cookie.httpOnly())")
    @Mapping(target = "persistent", expression = "java(cookie.persistent())")
    @Mapping(target = "secure", expression = "java(cookie.secure())")
    CookieDto toDto(Cookie cookie);

    @ObjectFactory
    default Cookie.Builder builder() {
        return new Cookie.Builder();
    }
}
