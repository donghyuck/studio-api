package studio.one.platform.objecttype.application.command;

public class ValidateUploadCommand {

    private final String fileName;
    private final String contentType;
    private final Long sizeBytes;

    public ValidateUploadCommand(String fileName, String contentType, Long sizeBytes) {
        this.fileName = fileName; this.contentType = contentType; this.sizeBytes = sizeBytes;
    }

    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public Long sizeBytes() { return sizeBytes; }
}
