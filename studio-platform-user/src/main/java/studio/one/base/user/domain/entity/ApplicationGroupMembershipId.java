package studio.one.base.user.domain.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ApplicationGroupMembershipId implements Serializable {
     
    @Column(name = "GROUP_ID", nullable = false)
    private Long groupId;
 
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

}