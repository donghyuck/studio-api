package studio.one.base.user.domain.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.base.user.util.ApplicationJpaNames;

@Getter
@Setter
@Entity(name=ApplicationJpaNames.Group.Role.ENTITY)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "TB_APPLICATION_GROUP_ROLES")
@NoArgsConstructor
@AllArgsConstructor 
@Builder
public class ApplicationGroupRole {

    @EmbeddedId
    private ApplicationGroupRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "GROUP_ID")
    private ApplicationGroup group;

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
