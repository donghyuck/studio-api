package studio.one.base.user.web.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.web.dto.GroupDto;

@Mapper(componentModel = "spring", uses = { TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationGroupMapper {

    @Mapping(target = "creationDate", source = "creationDate")
    @Mapping(target = "modifiedDate", source = "modifiedDate")   
    @Mapping(target = "properties", source = "properties", defaultExpression = "java(java.util.Collections.emptyMap())")
    GroupDto toDto(Group entity);

    @IterableMapping(elementTargetType = GroupDto.class)
    List<GroupDto> toDtos(List<Group> entities);

    @Mapping(target = "groupId",   ignore = true)
    @Mapping(target = "creationDate",  ignore = true)
    @Mapping(target = "modifiedDate",  ignore = true)
    @Mapping(target = "groupRoles",    ignore = true)
    @Mapping(target = "memberships",   ignore = true)
    @Mapping(target = "properties",    source = "properties", defaultExpression = "java(new java.util.HashMap<>())")
    ApplicationGroup toEntity(GroupDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "groupId",       ignore = true)  
    @Mapping(target = "creationDate",  ignore = true)   
    @Mapping(target = "modifiedDate",  ignore = true)  
    //@Mapping(target = "groupRoles",    ignore = true)   
    //@Mapping(target = "memberships",   ignore = true)
    void updateEntityFromDto(GroupDto dto, @MappingTarget Group entity);
   
    default int safeSize(java.util.Collection<?> c) {
        return c == null ? 0 : c.size();
    }
}
