package studio.one.platform.objecttype.service;

public record ObjectTypePolicyUpsertCommand(
        Integer maxFileMb,
        String allowedExt,
        String allowedMime,
        String policyJson,
        String updatedBy,
        Long updatedById,
        String createdBy,
        Long createdById
) {
}
