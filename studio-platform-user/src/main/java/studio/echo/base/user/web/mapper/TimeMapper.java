package studio.echo.base.user.web.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TimeMapper {
    default OffsetDateTime toOffsetDateTime(Date d) {
        return d == null ? null : OffsetDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC);
    }
}
