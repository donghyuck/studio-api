package studio.one.application.attachment.application.result;

public class AttachmentObjectTypeDescriptor {

    private final int objectType;
    private final boolean attachmentDomain;
    private final boolean attachmentEnabled;
    private final String attachmentType;

    public AttachmentObjectTypeDescriptor(
            int objectType,
            boolean attachmentDomain,
            boolean attachmentEnabled,
            String attachmentType) {
        this.objectType = objectType;
        this.attachmentDomain = attachmentDomain;
        this.attachmentEnabled = attachmentEnabled;
        this.attachmentType = attachmentType;
    }

    public int objectType() { return objectType; }

    public boolean attachmentDomain() { return attachmentDomain; }

    public boolean attachmentEnabled() { return attachmentEnabled; }

    public String attachmentType() { return attachmentType; }

public boolean isAttachmentDomainType() {
        return attachmentDomain || attachmentEnabled || hasAttachmentType();
    }

    public boolean hasAttachmentType() {
        return attachmentType != null && !attachmentType.isBlank();
    }
}
