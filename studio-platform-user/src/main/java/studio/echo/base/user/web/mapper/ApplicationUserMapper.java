package studio.echo.base.user.web.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.web.dto.ApplicationUserDto;
import studio.echo.base.user.web.dto.CreateUserRequest;
import studio.echo.base.user.web.dto.UpdateUserRequest;

@Mapper(componentModel = "spring", uses = { TimeMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ApplicationUserMapper {

    ApplicationUser toEntity(CreateUserRequest req);

    @Mapping(target = "username", ignore = true)  
    @Mapping(target = "password", ignore = true)  
    void updateEntityFromDto(UpdateUserRequest dto, @org.mapstruct.MappingTarget User entity);

    ApplicationUserDto toDto(User entity);

}
