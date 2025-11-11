package studio.one.base.security.acl.domain.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing object identities within the ACL system.
 */
@Getter
@Setter
@ToString(exclude = { "parent", "entries" })
@Entity
@Table(name = "acl_object_identity")
public class AclObjectIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id_class", nullable = false)
    private AclClassEntity aclClass;

    @Column(name = "object_id_identity", nullable = false, length = 36)
    private String objectIdIdentity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_object")
    private AclObjectIdentityEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_sid")
    private AclSidEntity ownerSid;

    @Column(name = "entries_inheriting", nullable = false)
    private boolean entriesInheriting;

    @OneToMany(mappedBy = "aclObjectIdentity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AclEntryEntity> entries = new LinkedHashSet<>();
}
