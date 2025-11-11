package studio.one.platform.ai.core.vector;

import java.util.List;

/**
 * Contract for persisting and querying vectors.
 */
public interface VectorStorePort {

    void upsert(List<VectorDocument> documents);

    List<VectorSearchResult> search(VectorSearchRequest request);
}
