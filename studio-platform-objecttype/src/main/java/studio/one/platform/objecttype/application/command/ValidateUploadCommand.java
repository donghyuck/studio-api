package studio.one.platform.objecttype.application.command;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidateUploadCommand)) {
            return false;
        }
        ValidateUploadCommand that = (ValidateUploadCommand) o;
        return Objects.equals(fileName, that.fileName)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(sizeBytes, that.sizeBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, contentType, sizeBytes);
    }
}
