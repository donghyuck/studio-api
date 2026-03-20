package studio.one.base.user.domain.entity;

import java.io.Serializable;
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

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "TB_APPLICATION_GROUP_MEMBERS")
@Entity(name = JpaEntityNames.GroupMembership.ENTITY)
public class ApplicationGroupMembership implements Serializable {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "userId", column = @Column(name = "USER_ID")),
            @AttributeOverride(name = "groupId", column = @Column(name = "GROUP_ID"))
    })
    private ApplicationGroupMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("groupId") 
    @JoinColumn(name = "GROUP_ID", referencedColumnName = "GROUP_ID", nullable = false) 
    private ApplicationGroup group;

    @Column(name = "JOINED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "JOINED_BY")
    private String joinedBy;


    public static ApplicationGroupMembership of(ApplicationGroup group, Long userId, String joinedBy) {
        ApplicationGroupMembership m = new ApplicationGroupMembership();
        m.setGroup(group);
        m.setId(new ApplicationGroupMembershipId(group.getGroupId(), userId));
        m.setJoinedBy(joinedBy);
        return m;
    }
}
