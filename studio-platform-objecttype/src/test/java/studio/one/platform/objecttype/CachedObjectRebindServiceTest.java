package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import studio.one.platform.objecttype.cache.CacheInvalidatable;
import studio.one.platform.objecttype.cache.CachedObjectRebindService;
import studio.one.platform.objecttype.lifecycle.ObjectRebindService;
import studio.one.platform.objecttype.yaml.YamlObjectRebindService;

class CachedObjectRebindServiceTest {

    @Test
    void rebindInvalidatesAllCachesAfterDelegateRebind() {
        RecordingRebindService delegate = new RecordingRebindService();
        RecordingCache registryCache = new RecordingCache();
        RecordingCache policyCache = new RecordingCache();
        CachedObjectRebindService service = new CachedObjectRebindService(delegate, registryCache, policyCache);

        service.rebind();

        assertEquals(1, delegate.rebindCalls);
        assertEquals(1, registryCache.invalidateAllCalls);
        assertEquals(1, policyCache.invalidateAllCalls);
        assertEquals(0, registryCache.invalidateTypeCalls);
        assertEquals(0, policyCache.invalidateTypeCalls);
    }

    @Test
    void rebindByTypeInvalidatesMatchingCacheEntries() {
        RecordingRebindService delegate = new RecordingRebindService();
        RecordingCache registryCache = new RecordingCache();
        RecordingCache policyCache = new RecordingCache();
        CachedObjectRebindService service = new CachedObjectRebindService(delegate, registryCache, policyCache);

        service.rebind(1200);

        assertEquals(1, delegate.rebindByTypeCalls);
        assertEquals(1200, delegate.lastObjectType);
        assertEquals(0, registryCache.invalidateAllCalls);
        assertEquals(0, policyCache.invalidateAllCalls);
        assertEquals(1, registryCache.invalidateTypeCalls);
        assertEquals(1, policyCache.invalidateTypeCalls);
        assertEquals(1200, registryCache.lastInvalidatedType);
        assertEquals(1200, policyCache.lastInvalidatedType);
    }

    @Test
    void cleanupStillInvalidatesCachesForYamlDelegate() {
        RecordingCache registryCache = new RecordingCache();
        RecordingCache policyCache = new RecordingCache();
        CachedObjectRebindService service = new CachedObjectRebindService(
                new YamlObjectRebindService(),
                registryCache,
                policyCache);

        service.cleanup();

        assertEquals(1, registryCache.invalidateAllCalls);
        assertEquals(1, policyCache.invalidateAllCalls);
    }

    private static final class RecordingRebindService implements ObjectRebindService {

        private int rebindCalls;
        private int rebindByTypeCalls;
        private int lastObjectType = -1;

        @Override
        public void rebind() {
            rebindCalls++;
        }

        @Override
        public void rebind(int objectType) {
            rebindByTypeCalls++;
            lastObjectType = objectType;
        }

        @Override
        public void cleanup() {
        }
    }

    private static final class RecordingCache implements CacheInvalidatable {

        private int invalidateAllCalls;
        private int invalidateTypeCalls;
        private int lastInvalidatedType = -1;

        @Override
        public void invalidateAll() {
            invalidateAllCalls++;
        }

        @Override
        public void invalidateType(int objectType) {
            invalidateTypeCalls++;
            lastInvalidatedType = objectType;
        }
    }
}
