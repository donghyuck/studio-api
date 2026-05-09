package studio.one.platform.objecttype.db.mybatis;

import java.util.Optional;

import studio.one.platform.objecttype.db.jdbc.model.JdbcObjectTypeMetadata;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

public class MyBatisObjectTypeRegistry implements ObjectTypeRegistry {

    private final ObjectTypeMyBatisStore store;

    public MyBatisObjectTypeRegistry(ObjectTypeMyBatisStore store) {
        this.store = store;
    }

    @Override
    public Optional<ObjectTypeMetadata> findByType(int objectType) {
        return store.findByType(objectType).map(JdbcObjectTypeMetadata::new);
    }

    @Override
    public Optional<ObjectTypeMetadata> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return store.findByCode(key).map(JdbcObjectTypeMetadata::new);
    }
}
