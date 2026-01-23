package studio.one.platform.objecttype.cache;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import studio.one.platform.domain.model.TypeObject;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;

public class CachedObjectPolicyResolver implements ObjectPolicyResolver, CacheInvalidatable {

    private final ObjectPolicyResolver delegate;
    private final Duration ttl;
    private final long maxSize;
    private final Cache<Integer, ObjectPolicy> byType;

    public CachedObjectPolicyResolver(ObjectPolicyResolver delegate, Duration ttl, long maxSize) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.byType = buildCache(ttl, maxSize);
    }

    @Override
    public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        return resolveByType(metadata.getObjectType(), () -> delegate.resolve(metadata));
    }

    @Override
    public Optional<ObjectPolicy> resolve(TypeObject object) {
        if (object == null) {
            return Optional.empty();
        }
        return resolveByType(object.getObjectType(), () -> delegate.resolve(object));
    }

    private Optional<ObjectPolicy> resolveByType(int objectType, java.util.function.Supplier<Optional<ObjectPolicy>> loader) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return loader.get();
        }
        ObjectPolicy cached = byType.getIfPresent(objectType);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ObjectPolicy> resolved = loader.get();
        resolved.ifPresent(policy -> byType.put(objectType, policy));
        return resolved;
    }

    @Override
    public void invalidateAll() {
        byType.invalidateAll();
    }

    @Override
    public void invalidateType(int objectType) {
        byType.invalidate(objectType);
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
