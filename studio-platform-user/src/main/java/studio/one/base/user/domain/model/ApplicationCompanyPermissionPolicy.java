package studio.one.base.user.domain.model;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.domain.support.JpaEntityNames;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TB_APPLICATION_COMPANY_PERMISSION_POLICY")
@Entity(name = JpaEntityNames.Company.PermissionPolicy.ENTITY)
public class ApplicationCompanyPermissionPolicy implements Serializable {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "companyId", column = @Column(name = "COMPANY_ID", nullable = false)),
            @AttributeOverride(name = "role", column = @Column(name = "ROLE", nullable = false, length = 30)),
            @AttributeOverride(name = "action", column = @Column(name = "ACTION_NAME", nullable = false, length = 100))
    })
    private ApplicationCompanyPermissionPolicyId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("companyId")
    @JoinColumn(name = "COMPANY_ID", referencedColumnName = "COMPANY_ID", nullable = false)
    private ApplicationCompany company;

    @Column(name = "ENABLED", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
