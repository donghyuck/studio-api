package studio.one.platform.objecttype.cache;

import studio.one.platform.objecttype.lifecycle.ObjectRebindService;

public class CachedObjectRebindService implements ObjectRebindService {

    private final ObjectRebindService delegate;
    private final CacheInvalidatable registryCache;
    private final CacheInvalidatable policyCache;

    public CachedObjectRebindService(ObjectRebindService delegate,
            CacheInvalidatable registryCache,
            CacheInvalidatable policyCache) {
        this.delegate = delegate;
        this.registryCache = registryCache;
        this.policyCache = policyCache;
    }

    @Override
    public void rebind() {
        if (delegate != null) {
            delegate.rebind();
        }
        invalidateAll();
    }

    @Override
    public void rebind(int objectType) {
        if (delegate != null) {
            delegate.rebind(objectType);
        }
        invalidateType(objectType);
    }

    @Override
    public void cleanup() {
        if (delegate != null) {
            delegate.cleanup();
        }
        invalidateAll();
    }

    private void invalidateAll() {
        if (registryCache != null) {
            registryCache.invalidateAll();
        }
        if (policyCache != null) {
            policyCache.invalidateAll();
        }
    }

    private void invalidateType(int objectType) {
        if (registryCache != null) {
            registryCache.invalidateType(objectType);
        }
        if (policyCache != null) {
            policyCache.invalidateType(objectType);
        }
    }
}
