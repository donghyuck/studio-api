package studio.one.base.user.web.dto;

import java.time.Instant;

import studio.one.base.user.company.model.CompanyMemberStatus;
import studio.one.base.user.company.model.CompanyRole;

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
