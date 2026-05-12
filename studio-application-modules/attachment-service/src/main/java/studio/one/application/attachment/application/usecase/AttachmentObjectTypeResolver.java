package studio.one.application.attachment.application.usecase;

import java.util.Optional;

import studio.one.application.attachment.application.result.AttachmentObjectTypeDescriptor;

public interface AttachmentObjectTypeResolver {

    Optional<AttachmentObjectTypeDescriptor> resolve(int objectType);

}
