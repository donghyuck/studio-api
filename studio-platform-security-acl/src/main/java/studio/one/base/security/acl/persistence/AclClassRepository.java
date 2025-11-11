package studio.one.base.security.acl.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.base.security.acl.domain.entity.AclClassEntity;

/**
 * Repository for ACL class metadata.
 */
@Repository
public interface AclClassRepository extends JpaRepository<AclClassEntity, Long> {

    Optional<AclClassEntity> findByClassName(String className);
}
