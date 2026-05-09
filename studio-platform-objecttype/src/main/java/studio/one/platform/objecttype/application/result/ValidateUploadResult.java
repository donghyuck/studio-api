package studio.one.platform.objecttype.application.result;

public record ValidateUploadResult(
        boolean allowed,
        String reason
) {
}
