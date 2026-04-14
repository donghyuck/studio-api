package studio.one.application.template.web.controller;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserDto;
import studio.one.platform.identity.UserRef;

final class TemplateWebSupport {

    private TemplateWebSupport() {
    }

    static UserDto findUserDto(ObjectProvider<IdentityService> identityServiceProvider, long userId) {
        if (userId <= 0) {
            return null;
        }
        IdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            return null;
        }
        return identityService.findById(userId)
                .map(TemplateWebSupport::toUserDto)
                .orElse(null);
    }

    private static UserDto toUserDto(UserRef userRef) {
        return new UserDto(userRef.userId(), userRef.username());
    }
}
