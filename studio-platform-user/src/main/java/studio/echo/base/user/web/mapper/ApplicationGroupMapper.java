package studio.echo.base.user.web.mapper;

import java.util.List;
import java.util.Set;

import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.web.dto.ApplicationGroupDto;

@Mapper(componentModel = "spring", uses = { TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationGroupMapper {

    @Mapping(target = "creationDate", source = "creationDate")
    @Mapping(target = "modifiedDate", source = "modifiedDate")
  //  @Mapping(target = "roleCount", expression = "java(safeSize(entity.getGroupRoles()))")
  //  @Mapping(target = "memberCount", expression = "java(safeSize(entity.getMemberships()))")
    @Mapping(target = "properties", source = "properties", defaultExpression = "java(java.util.Collections.emptyMap())")
    ApplicationGroupDto toDto(ApplicationGroup entity);

    @IterableMapping(elementTargetType = ApplicationGroupDto.class)
    List<ApplicationGroupDto> toDtos(List<ApplicationGroup> entities);

    @Mapping(target = "groupId",   ignore = true)
    @Mapping(target = "creationDate",  ignore = true)
    @Mapping(target = "modifiedDate",  ignore = true)
    @Mapping(target = "groupRoles",    ignore = true)
    @Mapping(target = "memberships",   ignore = true)
    @Mapping(target = "properties",    source = "properties", defaultExpression = "java(new java.util.HashMap<>())")
    ApplicationGroup toEntity(ApplicationGroupDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "groupId",       ignore = true)  
    @Mapping(target = "creationDate",  ignore = true)   
    @Mapping(target = "modifiedDate",  ignore = true)  
    @Mapping(target = "groupRoles",    ignore = true)   
    @Mapping(target = "memberships",   ignore = true)
    void updateEntityFromDto(ApplicationGroupDto dto, @MappingTarget ApplicationGroup entity);
   

    default int safeSize(Set<?> c) {
        return c == null ? 0 : c.size();
    }
}
