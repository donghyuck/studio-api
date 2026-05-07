package studio.one.base.user.web.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyMemberKeyCreateRequest(
        CompanyRole role,
        @Future
        Instant expiresAt,
        @Positive Integer maxUses) {
}
