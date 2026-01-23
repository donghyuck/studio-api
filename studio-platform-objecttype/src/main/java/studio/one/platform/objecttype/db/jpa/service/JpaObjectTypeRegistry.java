package studio.one.platform.objecttype.db.jpa.service;

import java.util.Optional;

import studio.one.platform.objecttype.db.jpa.entity.ObjectTypeEntity;
import studio.one.platform.objecttype.db.jpa.repo.ObjectTypeJpaRepository;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

public class JpaObjectTypeRegistry implements ObjectTypeRegistry {

    private final ObjectTypeJpaRepository repository;

    public JpaObjectTypeRegistry(ObjectTypeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ObjectTypeMetadata> findByType(int objectType) {
        return repository.findById(objectType).map(ObjectTypeEntity.class::cast);
    }

    @Override
    public Optional<ObjectTypeMetadata> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return repository.findByCode(key).map(ObjectTypeEntity.class::cast);
    }
}
