package studio.one.platform.objecttype.application.command;

public record ObjectTypePatchCommand(
        String code,
        String name,
        String domain,
        String status,
        String description,
        String updatedBy,
        Long updatedById
) {
}
