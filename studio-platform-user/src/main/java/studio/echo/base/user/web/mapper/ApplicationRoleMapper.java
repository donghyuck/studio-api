package studio.echo.base.user.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.web.dto.ApplicationRoleDto;

@Mapper(componentModel = "spring", uses = { TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationRoleMapper {
 
    ApplicationRoleDto toDto(Role entity);

}
