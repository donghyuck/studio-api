package studio.one.base.user.web.dto.response;

import java.time.Instant;

import studio.one.base.user.domain.model.company.CompanyMemberStatus;
import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyMemberDto(
        Long companyId,
        Long userId,
        CompanyRole role,
        CompanyMemberStatus status,
        Instant joinedAt,
        Long joinedBy,
        Instant updatedAt,
        Long updatedBy) {
}
