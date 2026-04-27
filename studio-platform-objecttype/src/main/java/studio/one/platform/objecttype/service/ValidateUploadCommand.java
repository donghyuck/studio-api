package studio.one.platform.objecttype.service;

public record ValidateUploadCommand(
        String fileName,
        String contentType,
        Long sizeBytes
) {
}
