package studio.one.base.user.web.dto;

import java.time.Instant;

import studio.one.base.user.company.model.CompanyMemberKeyStatus;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyMemberKeyDto(
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
