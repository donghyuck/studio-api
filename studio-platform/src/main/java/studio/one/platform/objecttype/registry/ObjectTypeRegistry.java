package studio.one.platform.objecttype.registry;

import java.util.Optional;

import studio.one.platform.objecttype.model.ObjectTypeMetadata;

/**
 * A registry for object type metadata.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectTypeRegistry {

    Optional<ObjectTypeMetadata> findByType(int objectType);

    Optional<ObjectTypeMetadata> findByKey(String key);

}
