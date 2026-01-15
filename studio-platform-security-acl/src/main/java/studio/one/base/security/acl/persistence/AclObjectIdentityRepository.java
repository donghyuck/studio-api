package studio.one.base.security.acl.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.base.security.acl.domain.entity.AclObjectIdentityEntity;

/**
 * Repository for object identities.
 */
@Repository
public interface AclObjectIdentityRepository extends JpaRepository<AclObjectIdentityEntity, Long> {

    List<AclObjectIdentityEntity> findByAclClass_Id(Long classId);

    Optional<AclObjectIdentityEntity> findByAclClass_IdAndObjectIdIdentity(Long classId, String objectIdIdentity);
}
