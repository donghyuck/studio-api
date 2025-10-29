package studio.echo.base.user.web.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.web.dto.UserDto;
import studio.echo.base.user.web.dto.CreateUserRequest;
import studio.echo.base.user.web.dto.UpdateUserRequest;

@Mapper(componentModel = "spring", uses = {
        TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationUserMapper {

    ApplicationUser toEntity(CreateUserRequest req);

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    void updateEntityFromDto(UpdateUserRequest dto, @org.mapstruct.MappingTarget User entity);

    @Mapping(target = "creationDate", source = "creationDate")
    @Mapping(target = "modifiedDate", source = "modifiedDate")
    UserDto toDto(User entity);

    List<UserDto> toDtos(List<User> entities);
}
