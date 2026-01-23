package studio.one.platform.objecttype.lifecycle;

import studio.one.platform.constant.ServiceNames;

/**
 * Handles rebind and cleanup lifecycles for object type metadata.
 *
 * @author donghyuck, son
 * @since 2026-01-22
 * @version 1.0
 */
public interface ObjectRebindService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:lifecycle:object-rebindservice";

    void rebind();

    void rebind(int objectType);

    void cleanup();

}
