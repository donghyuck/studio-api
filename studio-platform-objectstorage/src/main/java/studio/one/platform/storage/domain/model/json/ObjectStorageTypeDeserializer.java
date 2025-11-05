package studio.one.platform.storage.domain.model.json;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import studio.one.platform.storage.service.ObjectStorageType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectStorageTypeDeserializer extends JsonDeserializer<ObjectStorageType>  {

    @Override
    public ObjectStorageType deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getText();
        log.debug("ObjectStorageType value : {}", value);
        for (ObjectStorageType type : ObjectStorageType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ObjectStorageType: " + value);
    }
}