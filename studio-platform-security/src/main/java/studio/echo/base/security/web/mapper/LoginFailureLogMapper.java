package studio.echo.base.security.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.security.audit.domain.entity.LoginFailureLog;
import studio.echo.base.security.web.dto.LoginFailureLogDto;
import studio.echo.base.user.web.mapper.TimeMapper;


@Mapper(componentModel = "spring", uses = {
        TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface LoginFailureLogMapper {
    LoginFailureLogDto toDto(LoginFailureLog entity);

}
