package studio.one.base.security.acl.domain.port;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.base.security.acl.domain.model.AclSidEntity;

/**
 * Repository for ACL SIDs.
 */
@Repository
public interface AclSidRepository extends JpaRepository<AclSidEntity, Long> {

    Optional<AclSidEntity> findBySidAndPrincipal(String sid, boolean principal);
}
