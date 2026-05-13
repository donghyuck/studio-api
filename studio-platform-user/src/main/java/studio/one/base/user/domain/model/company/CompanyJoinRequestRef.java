package studio.one.base.user.domain.model.company;

import java.time.Instant;

public class CompanyJoinRequestRef {
    private final Long requestId;
    private final Long companyId;
    private final Long keyId;
    private final Long userId;
    private final String name;
    private final String email;
    private final String message;
    private final CompanyRole requestedRole;
    private final CompanyJoinRequestStatus status;
    private final Instant requestedAt;
    private final Long requestedBy;
    private final Instant decidedAt;
    private final Long decidedBy;

    public CompanyJoinRequestRef(Long requestId, Long companyId, Long keyId, Long userId, String name, String email, String message, CompanyRole requestedRole, CompanyJoinRequestStatus status, Instant requestedAt, Long requestedBy, Instant decidedAt, Long decidedBy) {
        this.requestId = requestId;
        this.companyId = companyId;
        this.keyId = keyId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.message = message;
        this.requestedRole = requestedRole;
        this.status = status;
        this.requestedAt = requestedAt;
        this.requestedBy = requestedBy;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    public Long requestId() {
        return requestId;
    }

    public Long companyId() {
        return companyId;
    }

    public Long keyId() {
        return keyId;
    }

    public Long userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String message() {
        return message;
    }

    public CompanyRole requestedRole() {
        return requestedRole;
    }

    public CompanyJoinRequestStatus status() {
        return status;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public Long decidedBy() {
        return decidedBy;
    }
}
