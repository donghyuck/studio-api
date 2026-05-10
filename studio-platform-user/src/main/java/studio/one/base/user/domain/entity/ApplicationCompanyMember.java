package studio.one.base.user.domain.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.domain.model.company.CompanyMemberRef;
import studio.one.base.user.domain.model.company.CompanyMemberStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.infrastructure.persistence.jpa.JpaEntityNames;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TB_APPLICATION_COMPANY_MEMBERS")
@Entity(name = JpaEntityNames.Company.Member.ENTITY)
public class ApplicationCompanyMember implements Serializable {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "companyId", column = @Column(name = "COMPANY_ID")),
            @AttributeOverride(name = "userId", column = @Column(name = "USER_ID"))
    })
    private ApplicationCompanyMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("companyId")
    @JoinColumn(name = "COMPANY_ID", referencedColumnName = "COMPANY_ID", nullable = false)
    private ApplicationCompany company;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 30)
    private CompanyRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private CompanyMemberStatus status = CompanyMemberStatus.ACTIVE;

    @Column(name = "JOINED_AT", nullable = false)
    private Instant joinedAt;

    @Column(name = "JOINED_BY")
    private Long joinedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    public CompanyMemberRef toRef() {
        return new CompanyMemberRef(
                id == null ? null : id.getCompanyId(),
                id == null ? null : id.getUserId(),
                role,
                status,
                joinedAt,
                joinedBy,
                updatedAt,
                updatedBy);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (joinedAt == null) {
            joinedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = joinedAt;
        }
        if (status == null) {
            status = CompanyMemberStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
