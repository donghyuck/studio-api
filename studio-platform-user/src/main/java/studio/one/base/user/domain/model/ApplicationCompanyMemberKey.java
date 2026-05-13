package studio.one.base.user.domain.model;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.domain.model.company.CompanyMemberKeyRef;
import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.support.JpaEntityNames;

@Entity(name = JpaEntityNames.Company.MemberKey.ENTITY)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_APPLICATION_COMPANY_MEMBER_KEY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCompanyMemberKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KEY_ID")
    private Long keyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "COMPANY_ID", referencedColumnName = "COMPANY_ID", nullable = false)
    private ApplicationCompany company;

    @Column(name = "COMPANY_ID", insertable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 30)
    private CompanyRole role;

    @Column(name = "KEY_HASH", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private CompanyMemberKeyStatus status = CompanyMemberKeyStatus.ACTIVE;

    @Column(name = "EXPIRES_AT")
    private Instant expiresAt;

    @Column(name = "MAX_USES")
    private Integer maxUses;

    @Column(name = "USED_COUNT", nullable = false)
    private int usedCount;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    public CompanyMemberKeyRef toRef(String plainMemberKey) {
        return new CompanyMemberKeyRef(
                keyId,
                companyId != null ? companyId : (company == null ? null : company.getCompanyId()),
                role,
                plainMemberKey,
                status,
                expiresAt,
                maxUses,
                usedCount,
                createdAt,
                createdBy);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = CompanyMemberKeyStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
