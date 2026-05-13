package studio.one.base.user.domain.model.company;

import java.time.Instant;

public class CompanyMemberRef {
    private final Long companyId;
    private final Long userId;
    private final CompanyRole role;
    private final CompanyMemberStatus status;
    private final Instant joinedAt;
    private final Long joinedBy;
    private final Instant updatedAt;
    private final Long updatedBy;

    public CompanyMemberRef(Long companyId, Long userId, CompanyRole role, CompanyMemberStatus status, Instant joinedAt, Long joinedBy, Instant updatedAt, Long updatedBy) {
        this.companyId = companyId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.joinedBy = joinedBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public Long companyId() {
        return companyId;
    }

    public Long userId() {
        return userId;
    }

    public CompanyRole role() {
        return role;
    }

    public CompanyMemberStatus status() {
        return status;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Long joinedBy() {
        return joinedBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long updatedBy() {
        return updatedBy;
    }
}
