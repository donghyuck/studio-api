package studio.one.application.attachment.web.controller;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.usecase.AttachmentObjectTypeResolver;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;

/**
 * Authorizes attachment-scoped RAG access through the same domain rules used by
 * attachment endpoints.
 */
public final class AttachmentRagObjectAuthorizer implements RagObjectAuthorizer {

    private final AttachmentService attachmentService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;
    private final ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;
    private final AttachmentObjectTypeResolver objectTypeResolver;

    public AttachmentRagObjectAuthorizer(
            AttachmentService attachmentService,
            ObjectProvider<PrincipalResolver> principalResolverProvider,
            ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers,
            AttachmentObjectTypeResolver objectTypeResolver) {
        this.attachmentService = attachmentService;
        this.principalResolverProvider = principalResolverProvider;
        this.ownerAccessAuthorizers = ownerAccessAuthorizers;
        this.objectTypeResolver = objectTypeResolver;
    }

    @Override
    public boolean supports(String objectType) {
        return "attachment".equalsIgnoreCase(objectType);
    }

    @Override
    public boolean canRead(String objectType, String objectId) {
        try {
            long attachmentId = Long.parseLong(objectId);
            if (attachmentId <= 0) {
                return false;
            }
            Attachment attachment = attachmentService.getAttachmentById(attachmentId);
            ApplicationPrincipal principal = AttachmentAccessSupport.requirePrincipal(principalResolverProvider);
            AttachmentAccessSupport.requireAttachmentAccess(
                    attachment,
                    principal,
                    ownerAccessAuthorizers,
                    objectTypeResolver,
                    AttachmentOwnerAccessAction.READ);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
