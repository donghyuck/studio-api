package studio.one.platform.objecttype.service;

import java.time.OffsetDateTime;

public record ObjectTypeView(
        int objectType,
        String code,
        String name,
        String domain,
        String status,
        String description,
        String createdBy,
        long createdById,
        OffsetDateTime createdAt,
        String updatedBy,
        long updatedById,
        OffsetDateTime updatedAt
) {
}
