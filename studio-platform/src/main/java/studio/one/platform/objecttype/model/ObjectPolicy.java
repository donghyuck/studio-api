package studio.one.platform.objecttype.model;

import java.util.Map;

/**
 * A policy definition attached to an object type or instance.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectPolicy {

    String getPolicyKey();

    Map<String, Object> getAttributes();

}
