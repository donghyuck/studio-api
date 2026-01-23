package studio.one.platform.objecttype.model;

import java.util.Map;

/**
 * Describes object type metadata stored in the registry.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectTypeMetadata {

    int getObjectType();

    String getKey();

    String getName();

    Map<String, Object> getAttributes();

}
