package studio.one.base.user.web.dto.request;

import java.time.Instant;

import javax.validation.constraints.Future;
import javax.validation.constraints.Positive;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyMemberKeyCreateRequest {
    private CompanyRole role;
    @Future
        private Instant expiresAt;
    @Positive private Integer maxUses;

    public CompanyMemberKeyCreateRequest() {
    }

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

    public CompanyRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setRole(CompanyRole role) {
        this.role = role;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }
}
