package studio.one.base.user.domain.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.util.ApplicationJpaNames;

@Getter
@Setter
@Entity(name= ApplicationJpaNames.User.Role.ENTITY)
@Table(name = "TB_APPLICATION_USER_ROLES")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationUserRole {

    @EmbeddedId
    private ApplicationUserRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "USER_ID")
    private ApplicationUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "ROLE_ID")
    private ApplicationRole role;

    @Column(name = "ASSIGNED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "ASSIGNED_BY")
    private String assignedBy;
}
