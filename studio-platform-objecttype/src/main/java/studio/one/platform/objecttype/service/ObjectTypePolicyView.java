package studio.one.platform.objecttype.service;

import java.time.OffsetDateTime;

public record ObjectTypePolicyView(
        int objectType,
        Integer maxFileMb,
        String allowedExt,
        String allowedMime,
        String policyJson,
        String createdBy,
        long createdById,
        OffsetDateTime createdAt,
        String updatedBy,
        long updatedById,
        OffsetDateTime updatedAt
) {
}
