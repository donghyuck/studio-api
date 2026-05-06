package studio.one.base.user.company.model;

import java.time.Instant;

public record CompanyMemberRef(
        Long companyId,
        Long userId,
        CompanyRole role,
        CompanyMemberStatus status,
        Instant joinedAt,
        Long joinedBy,
        Instant updatedAt,
        Long updatedBy) {
}
