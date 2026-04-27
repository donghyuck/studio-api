package studio.one.platform.objecttype.service;

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
