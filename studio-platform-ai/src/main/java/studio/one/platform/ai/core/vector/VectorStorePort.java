package studio.one.platform.ai.core.vector;

import java.util.List;

/**
 * Contract for persisting and querying vectors.
 */
public interface VectorStorePort {

    void upsert(List<VectorDocument> documents);

    List<VectorSearchResult> search(VectorSearchRequest request);

    /**
     * 지정된 objectType/objectId 조합의 벡터가 존재하는지 여부를 반환한다.
     */
    boolean exists(String objectType, String objectId);
}
