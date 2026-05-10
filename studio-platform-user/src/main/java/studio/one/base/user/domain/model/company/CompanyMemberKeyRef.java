package studio.one.base.user.domain.model.company;

import java.time.Instant;

public record CompanyMemberKeyRef(
        Long keyId,
        Long companyId,
        CompanyRole role,
        String memberKey,
        CompanyMemberKeyStatus status,
        Instant expiresAt,
        Integer maxUses,
        int usedCount,
        Instant createdAt,
        Long createdBy) {
}
