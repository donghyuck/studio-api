package studio.one.platform.objecttype.yaml;

import studio.one.platform.objecttype.lifecycle.ObjectRebindService;

/**
 * Rebind service for YAML-based registries.
 *
 * <p>In YAML mode, ObjectType definitions are loaded once at application startup and held
 * in-memory. There is no runtime reload of the YAML source. Therefore, {@code rebind()}
 * has no work to do here.
 *
 * <p>Cache invalidation (the actual observable effect of a rebind call in YAML mode) is
 * handled by {@code CachedObjectRebindService}, which wraps this service and clears the
 * registry and policy caches when {@code rebind()} is invoked. The data returned after
 * invalidation is the same as before, since the underlying YAML content does not change.
 *
 * <p>If runtime YAML reloading is required in the future, a separate implementation
 * should be introduced rather than modifying this class.
 */
public class YamlObjectRebindService implements ObjectRebindService {

    @Override
    public void rebind() {
        // YAML content is static; cache invalidation is handled by CachedObjectRebindService.
    }

    @Override
    public void rebind(int objectType) {
        // YAML content is static; cache invalidation is handled by CachedObjectRebindService.
    }

    @Override
    public void cleanup() {
        // nothing to clean up for a static YAML source
    }
}
