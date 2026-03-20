package studio.one.base.user.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.constant.JpaEntityNames;

@Getter
@Setter
@Entity(name = JpaEntityNames.User.Role.ENTITY)
@Table(name = "TB_APPLICATION_USER_ROLES")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationUserRole {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "userId", column = @Column(name = "USER_ID")),
            @AttributeOverride(name = "roleId", column = @Column(name = "ROLE_ID"))
    })
    private ApplicationUserRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "ROLE_ID")
    private ApplicationRole role;

    @Column(name = "ASSIGNED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "ASSIGNED_BY")
    private String assignedBy;

    public static ApplicationUserRole of(Long userId, ApplicationRole role, String assignedBy) {
        ApplicationUserRole ur = new ApplicationUserRole();
        ur.setId(new ApplicationUserRoleId(userId, role.getRoleId()));
        ur.setRole(role);
        ur.setAssignedBy(assignedBy);
        return ur;
    }
}
