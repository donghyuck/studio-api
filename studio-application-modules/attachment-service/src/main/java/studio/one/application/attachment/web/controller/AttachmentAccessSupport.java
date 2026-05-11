package studio.one.application.attachment.web.controller;

import java.util.Optional;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.result.AttachmentObjectTypeDescriptor;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentObjectTypeResolver;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.application.WellKnownAttachmentObjectTypes;

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
        requireAttachmentAccess(attachment, principal, null, AttachmentOwnerAccessAction.READ);
    }

    static void requireAttachmentAccess(
            Attachment attachment,
            ApplicationPrincipal principal,
            ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers,
            AttachmentOwnerAccessAction action) {
        requireAttachmentAccess(attachment, principal, ownerAccessAuthorizers, null, action);
    }

    static void requireAttachmentAccess(
            Attachment attachment,
            ApplicationPrincipal principal,
            ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers,
            AttachmentObjectTypeResolver objectTypeResolver,
            AttachmentOwnerAccessAction action) {
        if (isAdmin(principal)) {
            return;
        }
        if (isWellKnownDomainAttachmentType(attachment.getObjectType(), objectTypeResolver)) {
            requireOwnerAccess(
                    attachment.getObjectType(),
                    attachment.getObjectId(),
                    principal,
                    ownerAccessAuthorizers,
                    objectTypeResolver,
                    action);
            return;
        }
        long userId = requireUserId(principal);
        if (!Objects.equals(attachment.getCreatedBy(), userId)) {
            throw new AccessDeniedException("Forbidden attachment access");
        }
    }

    static void requireOwnerAccess(
            int objectType,
            long objectId,
            ApplicationPrincipal principal,
            ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers,
            AttachmentOwnerAccessAction action) {
        requireOwnerAccess(objectType, objectId, principal, ownerAccessAuthorizers, null, action);
    }

    static void requireOwnerAccess(
            int objectType,
            long objectId,
            ApplicationPrincipal principal,
            ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers,
            AttachmentObjectTypeResolver objectTypeResolver,
            AttachmentOwnerAccessAction action) {
        if (isAdmin(principal)) {
            return;
        }
        if (!isWellKnownDomainAttachmentType(objectType, objectTypeResolver)) {
            return;
        }
        if (ownerAccessAuthorizers == null) {
            throw new AccessDeniedException("Forbidden attachment access");
        }
        boolean allowed = ownerAccessAuthorizers.orderedStream()
                .filter(authorizer -> authorizer.supports(objectType))
                .anyMatch(authorizer -> authorizer.canAccess(objectType, objectId, principal, action));
        if (!allowed) {
            throw new AccessDeniedException("Forbidden attachment access");
        }
    }

    static boolean isWellKnownDomainAttachmentType(int objectType) {
        return isWellKnownDomainAttachmentType(objectType, null);
    }

    static boolean isWellKnownDomainAttachmentType(int objectType, AttachmentObjectTypeResolver objectTypeResolver) {
        if (objectType == WellKnownAttachmentObjectTypes.GENERIC_ATTACHMENT) {
            return false;
        }
        Optional<AttachmentObjectTypeDescriptor> resolved = resolveAttachmentTypeDescriptor(objectType, objectTypeResolver);
        if (resolved.isPresent() && resolved.get().isAttachmentDomainType()) {
            return true;
        }
        return isFallbackWellKnownDomainAttachmentType(objectType);
    }

    private static boolean isFallbackWellKnownDomainAttachmentType(int objectType) {
        return objectType == WellKnownAttachmentObjectTypes.POST_ATTACHMENT
                || objectType == WellKnownAttachmentObjectTypes.MAIL_ATTACHMENT
                || objectType == WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT
                || objectType == WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT;
    }

    private static Optional<AttachmentObjectTypeDescriptor> resolveAttachmentTypeDescriptor(
            int objectType,
            AttachmentObjectTypeResolver objectTypeResolver) {
        if (objectTypeResolver == null) {
            return Optional.empty();
        }
        try {
            return objectTypeResolver.resolve(objectType);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
