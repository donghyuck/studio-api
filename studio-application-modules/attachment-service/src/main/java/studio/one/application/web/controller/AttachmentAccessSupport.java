package studio.one.application.web.controller;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;

final class AttachmentAccessSupport {

    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "ROLE_ADMIN");

    private AttachmentAccessSupport() {
    }

    static ApplicationPrincipal requirePrincipal(ObjectProvider<PrincipalResolver> principalResolverProvider) {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new AuthenticationCredentialsNotFoundException("No principal resolver configured");
        }
        ApplicationPrincipal principal = resolver.currentOrNull();
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return principal;
    }

    static boolean isAdmin(ApplicationPrincipal principal) {
        return principal != null && principal.roles().stream().anyMatch(ADMIN_ROLES::contains);
    }

    static long requireUserId(ApplicationPrincipal principal) {
        if (principal != null && principal.getUserId() != null && principal.getUserId() > 0) {
            return principal.getUserId();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    static long requireUserId(Long userId) {
        if (userId != null && userId > 0) {
            return userId;
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    static void requireAttachmentAccess(Attachment attachment, ApplicationPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        long userId = requireUserId(principal);
        if (!Objects.equals(attachment.getCreatedBy(), userId)) {
            throw new AccessDeniedException("Forbidden attachment access");
        }
    }
}
