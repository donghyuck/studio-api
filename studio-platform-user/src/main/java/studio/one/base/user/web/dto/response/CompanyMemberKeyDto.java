package studio.one.base.user.web.dto.response;

import java.time.Instant;

import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyMemberKeyDto {
    private final Long keyId;
    private final Long companyId;
    private final CompanyRole role;
    private final String memberKey;
    private final CompanyMemberKeyStatus status;
    private final Instant expiresAt;
    private final Integer maxUses;
    private final int usedCount;
    private final Instant createdAt;
    private final Long createdBy;

    public CompanyMemberKeyDto(Long keyId, Long companyId, CompanyRole role, String memberKey, CompanyMemberKeyStatus status, Instant expiresAt, Integer maxUses, int usedCount, Instant createdAt, Long createdBy) {
        this.keyId = keyId;
        this.companyId = companyId;
        this.role = role;
        this.memberKey = memberKey;
        this.status = status;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.usedCount = usedCount;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long keyId() {
        return keyId;
    }

    public Long companyId() {
        return companyId;
    }

    public CompanyRole role() {
        return role;
    }

    public String memberKey() {
        return memberKey;
    }

    public CompanyMemberKeyStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Integer maxUses() {
        return maxUses;
    }

    public int usedCount() {
        return usedCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long createdBy() {
        return createdBy;
    }
}
