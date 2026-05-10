package studio.one.base.user.web.dto.response;

import java.time.Instant;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyRole;

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
