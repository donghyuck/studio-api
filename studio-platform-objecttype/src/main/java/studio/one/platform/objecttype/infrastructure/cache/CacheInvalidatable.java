package studio.one.platform.objecttype.infrastructure.cache;

public interface CacheInvalidatable {

    void invalidateAll();

    void invalidateType(int objectType);
}
