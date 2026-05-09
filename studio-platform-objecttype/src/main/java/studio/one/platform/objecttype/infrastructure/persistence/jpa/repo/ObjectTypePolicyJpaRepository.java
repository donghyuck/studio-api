package studio.one.platform.objecttype.infrastructure.persistence.jpa.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.platform.objecttype.infrastructure.persistence.jpa.entity.ObjectTypePolicyEntity;

public interface ObjectTypePolicyJpaRepository extends JpaRepository<ObjectTypePolicyEntity, Integer> {
}
