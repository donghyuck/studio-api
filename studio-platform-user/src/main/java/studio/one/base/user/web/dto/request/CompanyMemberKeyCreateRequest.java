package studio.one.base.user.web.dto.request;

import java.time.Instant;

import javax.validation.constraints.Future;
import javax.validation.constraints.Positive;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyMemberKeyCreateRequest {
    private final CompanyRole role;
    @Future
        private final Instant expiresAt;
    @Positive private final Integer maxUses;

    public CompanyMemberKeyCreateRequest(CompanyRole role, @Future
        Instant expiresAt, @Positive Integer maxUses) {
        this.role = role;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
    }

    public CompanyRole role() {
        return role;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Integer maxUses() {
        return maxUses;
    }
}
