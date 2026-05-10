package studio.one.base.user.web.dto.response;

import java.time.Instant;

import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;

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
