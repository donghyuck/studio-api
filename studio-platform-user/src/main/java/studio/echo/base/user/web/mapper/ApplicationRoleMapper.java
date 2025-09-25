package studio.echo.base.user.web.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.web.dto.RoleDto;

@Mapper(componentModel = "spring", uses = {
        TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationRoleMapper {

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modifiedDate", ignore = true)
    ApplicationRole toEntity(RoleDto dto);

    RoleDto toDto(Role entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modifiedDate", ignore = true) 
    void updateEntityFromDto(RoleDto dto, @MappingTarget ApplicationRole entity);

}
