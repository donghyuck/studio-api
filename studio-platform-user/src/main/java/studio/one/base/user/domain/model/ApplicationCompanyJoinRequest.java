package studio.one.base.user.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.support.JpaEntityNames;

@Entity(name = JpaEntityNames.Company.JoinRequest.ENTITY)
@Table(name = "TB_APPLICATION_COMPANY_JOIN_REQUEST")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCompanyJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REQUEST_ID")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "COMPANY_ID", referencedColumnName = "COMPANY_ID", nullable = false)
    private ApplicationCompany company;

    @Column(name = "COMPANY_ID", insertable = false, updatable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "KEY_ID", referencedColumnName = "KEY_ID", nullable = false)
    private ApplicationCompanyMemberKey memberKey;

    @Column(name = "KEY_ID", insertable = false, updatable = false)
    private Long keyId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "REQUEST_NAME", length = 255)
    private String name;

    @Column(name = "EMAIL", length = 255)
    private String email;

    @Column(name = "MESSAGE", length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "REQUESTED_ROLE", nullable = false, length = 30)
    private CompanyRole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private CompanyJoinRequestStatus status = CompanyJoinRequestStatus.PENDING;

    @Column(name = "REQUESTED_AT", nullable = false)
    private Instant requestedAt;

    @Column(name = "REQUESTED_BY")
    private Long requestedBy;

    @Column(name = "DECIDED_AT")
    private Instant decidedAt;

    @Column(name = "DECIDED_BY")
    private Long decidedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    public CompanyJoinRequestRef toRef() {
        return new CompanyJoinRequestRef(
                requestId,
                companyId != null ? companyId : (company == null ? null : company.getCompanyId()),
                keyId != null ? keyId : (memberKey == null ? null : memberKey.getKeyId()),
                userId,
                name,
                email,
                message,
                requestedRole,
                status,
                requestedAt,
                requestedBy,
                decidedAt,
                decidedBy);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = requestedAt;
        }
        if (status == null) {
            status = CompanyJoinRequestStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
