package studio.one.platform.objecttype.yaml;

import java.util.Collections;
import java.util.Map;

import studio.one.platform.objecttype.model.ObjectTypeMetadata;

/**
 * In-memory object type metadata loaded from YAML.
 */
public class YamlObjectTypeMetadata implements ObjectTypeMetadata {

    private final int objectType;
    private final String key;
    private final String name;
    private final Map<String, Object> attributes;

    public YamlObjectTypeMetadata(int objectType, String key, String name, Map<String, Object> attributes) {
        this.objectType = objectType;
        this.key = key;
        this.name = name;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    @Override
    public int getObjectType() {
        return objectType;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
