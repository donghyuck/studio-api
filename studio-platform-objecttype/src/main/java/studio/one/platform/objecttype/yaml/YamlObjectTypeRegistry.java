package studio.one.platform.objecttype.yaml;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

/**
 * YAML-backed registry using in-memory maps.
 */
public class YamlObjectTypeRegistry implements ObjectTypeRegistry {

    private final Map<Integer, ObjectTypeMetadata> byType;
    private final Map<String, ObjectTypeMetadata> byKey;

    public YamlObjectTypeRegistry(Map<Integer, ObjectTypeMetadata> byType,
            Map<String, ObjectTypeMetadata> byKey) {
        this.byType = byType == null ? Collections.emptyMap() : Collections.unmodifiableMap(byType);
        this.byKey = byKey == null ? Collections.emptyMap() : Collections.unmodifiableMap(byKey);
    }

    @Override
    public Optional<ObjectTypeMetadata> findByType(int objectType) {
        return Optional.ofNullable(byType.get(objectType));
    }

    @Override
    public Optional<ObjectTypeMetadata> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key));
    }
}
