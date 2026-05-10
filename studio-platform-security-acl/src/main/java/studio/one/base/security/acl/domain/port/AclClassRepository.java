package studio.one.base.security.acl.domain.port;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.base.security.acl.domain.model.AclClassEntity;

/**
 * Repository for ACL class metadata.
 */
@Repository
public interface AclClassRepository extends JpaRepository<AclClassEntity, Long> {

    Optional<AclClassEntity> findByClassName(String className);
}
