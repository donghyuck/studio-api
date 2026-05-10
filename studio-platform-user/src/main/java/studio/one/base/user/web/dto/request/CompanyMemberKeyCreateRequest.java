package studio.one.base.user.web.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyMemberKeyCreateRequest(
        CompanyRole role,
        @Future
        Instant expiresAt,
        @Positive Integer maxUses) {
}
