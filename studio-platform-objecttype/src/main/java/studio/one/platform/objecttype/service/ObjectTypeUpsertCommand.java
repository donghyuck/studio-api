package studio.one.platform.objecttype.service;

public record ObjectTypeUpsertCommand(
        Integer objectType,
        String code,
        String name,
        String domain,
        String status,
        String description,
        String updatedBy,
        Long updatedById,
        String createdBy,
        Long createdById
) {
}
