package studio.one.base.user.domain.entity;

import java.io.Serializable;
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
import studio.one.base.user.constant.JpaEntityNames;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TB_APPLICATION_GROUP_MEMBERS")
@Entity(name = JpaEntityNames.GroupMembership.ENTITY)
public class ApplicationGroupMembership implements Serializable {

    @EmbeddedId
    private ApplicationGroupMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId") 
    @JoinColumn(name = "USER_ID", referencedColumnName = "USER_ID", nullable = false)
    private ApplicationUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("groupId") 
    @JoinColumn(name = "GROUP_ID", referencedColumnName = "GROUP_ID", nullable = false) 
    private ApplicationGroup group;

    @Column(name = "JOINED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "JOINED_BY")
    private String joinedBy;


    public static ApplicationGroupMembership of(ApplicationGroup group, ApplicationUser user, String joinedBy) {
        ApplicationGroupMembership m = new ApplicationGroupMembership();
        m.setGroup(group);
        m.setUser(user);
        m.setId(new ApplicationGroupMembershipId(group.getGroupId(), user.getUserId()));
        m.setJoinedBy(joinedBy);
        return m;
    }
}
