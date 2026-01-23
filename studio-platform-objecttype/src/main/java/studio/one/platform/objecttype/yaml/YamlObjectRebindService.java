package studio.one.platform.objecttype.yaml;

import studio.one.platform.objecttype.lifecycle.ObjectRebindService;

/**
 * No-op rebind service for YAML-based registry.
 */
public class YamlObjectRebindService implements ObjectRebindService {

    @Override
    public void rebind() {
        // no-op
    }

    @Override
    public void rebind(int objectType) {
        // no-op
    }

    @Override
    public void cleanup() {
        // no-op
    }
}
