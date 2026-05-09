package studio.one.platform.objecttype.application.command;

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
