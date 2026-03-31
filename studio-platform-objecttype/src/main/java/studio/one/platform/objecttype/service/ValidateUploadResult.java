package studio.one.platform.objecttype.service;

public record ValidateUploadResult(
        boolean allowed,
        String reason
) {
}
