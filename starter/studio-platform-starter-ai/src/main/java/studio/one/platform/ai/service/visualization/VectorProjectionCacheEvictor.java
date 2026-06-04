package studio.one.platform.ai.service.visualization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import studio.one.platform.ai.core.rag.event.RagObjectDeletedEvent;
import studio.one.platform.ai.core.rag.event.RagObjectIndexedEvent;

/**
 * Event listener that evicts the vector projection points cache when RAG indexing events occur.
 */
@Component
public class VectorProjectionCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(VectorProjectionCacheEvictor.class);
    private static final String CACHE_NAME = "vectorProjectionPoints";

    private final CacheManager cacheManager;

    public VectorProjectionCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts the vector projection points cache when a RAG object has been successfully indexed.
     *
     * @param event the RAG object indexed event
     */
    @EventListener
    public void handleRagObjectIndexed(RagObjectIndexedEvent event) {
        log.info("RAG object indexed event received. Evicting vector projection points cache. objectType: {}, objectId: {}",
                event.objectType(), event.objectId());
        clearCache();
    }

    /**
     * Evicts the vector projection points cache when a RAG object history has been deleted.
     *
     * @param event the RAG object deleted event
     */
    @EventListener
    public void handleRagObjectDeleted(RagObjectDeletedEvent event) {
        log.info("RAG object deleted event received. Evicting vector projection points cache. objectType: {}, objectId: {}",
                event.objectType(), event.objectId());
        clearCache();
    }

    private void clearCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
            log.debug("Successfully cleared {} cache.", CACHE_NAME);
        } else {
            log.warn("Cache {} not found in CacheManager", CACHE_NAME);
        }
    }
}
