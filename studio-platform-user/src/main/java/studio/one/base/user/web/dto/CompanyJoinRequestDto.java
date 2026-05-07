package studio.one.base.user.web.dto;

import java.time.Instant;

import studio.one.base.user.company.model.CompanyJoinRequestStatus;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyJoinRequestDto(
        Long requestId,
        Long companyId,
        Long keyId,
        Long userId,
        String name,
        String email,
        String message,
        CompanyRole requestedRole,
        CompanyJoinRequestStatus status,
        Instant requestedAt,
        Long requestedBy,
        Instant decidedAt,
        Long decidedBy) {
}
