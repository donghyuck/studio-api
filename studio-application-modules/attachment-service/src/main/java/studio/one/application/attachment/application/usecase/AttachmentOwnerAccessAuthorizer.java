package studio.one.application.attachment.application.usecase;

import studio.one.application.attachment.application.command.*;
import studio.one.application.attachment.application.result.*;
import studio.one.application.attachment.application.service.AttachmentDownloadTokenCodec;

import studio.one.platform.identity.ApplicationPrincipal;

public interface AttachmentOwnerAccessAuthorizer {

    boolean supports(int objectType);

    boolean canAccess(
            int objectType,
            long objectId,
            ApplicationPrincipal principal,
            AttachmentOwnerAccessAction action);
}
