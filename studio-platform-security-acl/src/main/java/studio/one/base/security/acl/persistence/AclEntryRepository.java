package studio.one.base.security.acl.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.one.base.security.acl.domain.entity.AclEntryEntity;

/**
 * Repository for ACL entries with helper query methods used by the ACL module.
 */
@Repository
public interface AclEntryRepository extends JpaRepository<AclEntryEntity, Long> {

    /**
     * Fetches ACL rows joined with object identity, class and SID information
     * required for policy aggregation.
     *
     * @return list of projection rows
     */
    @Query("""
            select c.className as className,
                   oi.objectIdIdentity as objectIdentity,
                   s.sid as sid,
                   s.principal as principal,
                   e.mask as mask,
                   e.granting as granting
            from AclEntryEntity e
            join e.aclObjectIdentity oi
            join oi.aclClass c
            join e.sid s
            """)
    List<AclPolicyProjection> findAllForPolicy();

    /**
     * Returns the largest ace_order value for a given object identity.
     */
    @Query("select max(e.aceOrder) from AclEntryEntity e where e.aclObjectIdentity.id = :objectId")
    Integer findMaxAceOrderByAclObjectIdentity_Id(@Param("objectId") Long objectIdentityId);
}
