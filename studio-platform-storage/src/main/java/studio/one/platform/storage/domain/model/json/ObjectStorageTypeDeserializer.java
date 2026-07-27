package studio.one.platform.storage.domain.model.json;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import studio.one.platform.storage.domain.model.ObjectStorageType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectStorageTypeDeserializer extends ValueDeserializer<ObjectStorageType> {
    @Override
    public ObjectStorageType deserialize(JsonParser parser, DeserializationContext context) {
        String value = parser.getString();
        log.debug("ObjectStorageType value : {}", value);
        for (ObjectStorageType type : ObjectStorageType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ObjectStorageType: " + value);
    }
}
