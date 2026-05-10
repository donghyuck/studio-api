package studio.one.application.attachment.service;

import studio.one.platform.identity.ApplicationPrincipal;

public interface AttachmentOwnerAccessAuthorizer {

    boolean supports(int objectType);

    boolean canAccess(
            int objectType,
            long objectId,
            ApplicationPrincipal principal,
            AttachmentOwnerAccessAction action);
}
