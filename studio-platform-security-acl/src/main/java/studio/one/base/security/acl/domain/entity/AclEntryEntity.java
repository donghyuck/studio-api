package studio.one.base.security.acl.domain.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing ACL entries that tie identities to permissions.
 */
@Getter
@Setter
@ToString(exclude = { "aclObjectIdentity", "sid" })
@Entity
@Table(name = "acl_entry")
public class AclEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acl_object_identity", nullable = false)
    private AclObjectIdentityEntity aclObjectIdentity;

    @Column(name = "ace_order", nullable = false)
    private Integer aceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sid", nullable = false)
    private AclSidEntity sid;

    @Column(name = "mask", nullable = false)
    private Integer mask;

    @Column(name = "granting", nullable = false)
    private boolean granting;

    @Column(name = "audit_success", nullable = false)
    private boolean auditSuccess;

    @Column(name = "audit_failure", nullable = false)
    private boolean auditFailure;
}
