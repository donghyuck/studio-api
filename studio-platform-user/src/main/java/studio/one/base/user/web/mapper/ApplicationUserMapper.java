package studio.one.base.user.web.mapper;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.web.dto.CreateUserRequest;
import studio.one.base.user.web.dto.UpdateUserRequest;
import studio.one.base.user.web.dto.UserDto;

@Mapper(componentModel = "spring", uses = {
        TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationUserMapper {

    @Mapping(target = "username", source = "username")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "password", source = "password")
    ApplicationUser toEntity(CreateUserRequest req);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    void updateEntityFromDto(UpdateUserRequest dto, @org.mapstruct.MappingTarget ApplicationUser entity);

    /**
     * Fallback for non-ApplicationUser implementations.
     */
    default void updateEntityFromDto(UpdateUserRequest dto, @org.mapstruct.MappingTarget User entity) {
        if (entity instanceof ApplicationUser appUser) {
            updateEntityFromDto(dto, appUser);
        } else {
            throw new IllegalArgumentException("Unsupported user type: " + entity.getClass());
        }
    }

    @Mapping(target = "creationDate", source = "creationDate")
    @Mapping(target = "modifiedDate", source = "modifiedDate")
    UserDto toDto(User entity);

    List<UserDto> toDtos(List<User> entities);
    
}
