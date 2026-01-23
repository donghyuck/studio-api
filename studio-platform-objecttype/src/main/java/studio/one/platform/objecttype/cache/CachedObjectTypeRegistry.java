package studio.one.platform.objecttype.cache;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

public class CachedObjectTypeRegistry implements ObjectTypeRegistry, CacheInvalidatable {

    private final ObjectTypeRegistry delegate;
    private final Duration ttl;
    private final long maxSize;
    private final Cache<Integer, ObjectTypeMetadata> byType;
    private final Cache<String, ObjectTypeMetadata> byKey;

    public CachedObjectTypeRegistry(ObjectTypeRegistry delegate, Duration ttl, long maxSize) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.byType = buildCache(ttl, maxSize);
        this.byKey = buildCache(ttl, maxSize);
    }

    @Override
    public Optional<ObjectTypeMetadata> findByType(int objectType) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return delegate.findByType(objectType);
        }
        ObjectTypeMetadata cached = byType.getIfPresent(objectType);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ObjectTypeMetadata> resolved = delegate.findByType(objectType);
        resolved.ifPresent(this::cache);
        return resolved;
    }

    @Override
    public Optional<ObjectTypeMetadata> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return delegate.findByKey(key);
        }
        ObjectTypeMetadata cached = byKey.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ObjectTypeMetadata> resolved = delegate.findByKey(key);
        resolved.ifPresent(this::cache);
        return resolved;
    }

    @Override
    public void invalidateAll() {
        byType.invalidateAll();
        byKey.invalidateAll();
    }

    @Override
    public void invalidateType(int objectType) {
        ObjectTypeMetadata removed = byType.getIfPresent(objectType);
        if (removed != null && removed.getKey() != null) {
            byKey.invalidate(removed.getKey());
        }
        byType.invalidate(objectType);
    }

    private void cache(ObjectTypeMetadata meta) {
        byType.put(meta.getObjectType(), meta);
        String key = meta.getKey();
        if (key != null) {
            byKey.put(key, meta);
        }
    }

    private static <K, V> Cache<K, V> buildCache(Duration ttl, long maxSize) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Caffeine.newBuilder().maximumSize(0).build();
        }
        Caffeine<Object, Object> builder = Caffeine.newBuilder().expireAfterWrite(ttl);
        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }
        return builder.build();
    }
}
