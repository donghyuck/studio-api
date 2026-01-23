package studio.one.platform.objecttype.db.jpa.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.platform.objecttype.db.jpa.entity.ObjectTypeEntity;

public interface ObjectTypeJpaRepository extends JpaRepository<ObjectTypeEntity, Integer> {

    Optional<ObjectTypeEntity> findByCode(String code);
}
