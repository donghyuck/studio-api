package studio.one.platform.objecttype.cache;

public interface CacheInvalidatable {

    void invalidateAll();

    void invalidateType(int objectType);
}
