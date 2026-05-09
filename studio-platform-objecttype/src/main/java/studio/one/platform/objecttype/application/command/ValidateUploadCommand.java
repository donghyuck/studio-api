package studio.one.platform.objecttype.application.command;

public record ValidateUploadCommand(
        String fileName,
        String contentType,
        Long sizeBytes
) {
}
