package studio.one.base.security.acl.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a security identity (SID) for Spring Security ACL.
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "acl_sid")
public class AclSidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    @Column(name = "sid", nullable = false, length = 100)
    private String sid;
}
