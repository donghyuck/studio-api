package studio.one.application.attachment.application.result;

public record AttachmentObjectTypeDescriptor(
        int objectType,
        boolean attachmentDomain,
        boolean attachmentEnabled,
        String attachmentType) {

    public boolean isAttachmentDomainType() {
        return attachmentDomain || attachmentEnabled || hasAttachmentType();
    }

    public boolean hasAttachmentType() {
        return attachmentType != null && !attachmentType.isBlank();
    }
}
