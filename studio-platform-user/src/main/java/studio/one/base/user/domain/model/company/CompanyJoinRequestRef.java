package studio.one.base.user.domain.model.company;

import java.time.Instant;

public record CompanyJoinRequestRef(
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
