package studio.one.platform.objecttype.db.jdbc;

import java.util.Optional;

import studio.one.platform.objecttype.db.jdbc.model.JdbcObjectTypeMetadata;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

public class JdbcObjectTypeRegistry implements ObjectTypeRegistry {

    private final ObjectTypeJdbcRepository repository;

    public JdbcObjectTypeRegistry(ObjectTypeJdbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ObjectTypeMetadata> findByType(int objectType) {
        return repository.findByType(objectType).map(JdbcObjectTypeMetadata::new);
    }

    @Override
    public Optional<ObjectTypeMetadata> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return repository.findByCode(key).map(JdbcObjectTypeMetadata::new);
    }
}
