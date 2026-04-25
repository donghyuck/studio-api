package studio.one.platform.ai.core.vector;

import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * Contract for persisting and querying vectors.
 */
public interface VectorStorePort {

    void upsert(List<VectorDocument> documents);

    default void upsert(VectorRecord record) {
        Objects.requireNonNull(record, "record");
        upsertAll(List.of(record));
    }

    default void upsertAll(List<VectorRecord> records) {
        Objects.requireNonNull(records, "records");
        if (records.isEmpty()) {
            return;
        }
        upsert(records.stream()
                .map(VectorRecord::toVectorDocument)
                .toList());
    }

    default void deleteByObject(String objectType, String objectId) {
        throw new UnsupportedOperationException("deleteByObject is not implemented");
    }

    /**
     * Replaces all vectors for an object scope.
     * <p>
     * Default implementation is non-atomic because it delegates to
     * {@link #deleteByObject(String, String)} and then {@link #upsert(List)}.
     * Implementations that support transactions should override this method with an
     * atomic replacement.
     */
    default void replaceByObject(String objectType, String objectId, List<VectorDocument> documents) {
        deleteByObject(objectType, objectId);
        upsert(documents);
    }

    List<VectorSearchResult> search(VectorSearchRequest request);

    default VectorSearchResults searchRecords(VectorSearchRequest request) {
        long startedAt = System.nanoTime();
        List<VectorSearchHit> hits = search(request).stream()
                .map(result -> VectorSearchHit.from(result, request.includeText(), request.includeMetadata()))
                .toList();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return new VectorSearchResults(hits, elapsedMs);
    }

    default VectorSearchResults searchWithFilter(VectorSearchRequest request) {
        return searchRecords(request);
    }

    default void deleteByDocumentId(String documentId) {
        throw new UnsupportedOperationException("deleteByDocumentId is not implemented");
    }

    default void deleteByChunkId(String chunkId) {
        throw new UnsupportedOperationException("deleteByChunkId is not implemented");
    }

    default boolean existsByContentHash(String contentHash) {
        return false;
    }

    /**
     * 지정된 objectType/objectId 조합의 벡터가 존재하는지 여부를 반환한다.
     */
    boolean exists(String objectType, String objectId);

    /**
     * objectType/objectId로 제한된 범위에서 검색한다.
     */
    default List<VectorSearchResult> searchByObject(String objectType, String objectId, VectorSearchRequest request) {
        // 기본 구현은 전체 검색 후 필터; 구체 구현에서 최적화 가능
        return search(request).stream()
                .filter(r -> {
                    Map<String, Object> meta = r.document().metadata();
                    if (meta == null) return false;
                    boolean typeOk = objectType == null || objectType.isBlank()
                            || objectType.equalsIgnoreCase(Objects.toString(meta.get("objectType"), ""));
                    boolean idOk = objectId == null || objectId.isBlank()
                            || objectId.equals(Objects.toString(meta.get("objectId"), ""));
                    return typeOk && idOk;
                })
                .limit(request.topK())
                .toList();
    }

    /**
     * BM25(문자열) + 벡터 결과를 하이브리드로 검색한다.
     */
    default List<VectorSearchResult> hybridSearch(String query, VectorSearchRequest request, double vectorWeight, double lexicalWeight) {
        return search(request);
    }

    /**
     * BM25 + 벡터 하이브리드 검색 (objectType/objectId 필터 포함).
     */
    default List<VectorSearchResult> hybridSearchByObject(String query, String objectType, String objectId, VectorSearchRequest request, double vectorWeight, double lexicalWeight) {
        return searchByObject(objectType, objectId, request);
    }

    /**
     * objectType/objectId에 속한 벡터를 chunk_index 순서로 가져온다.
     * limit가 null이면 전체를 반환한다.
     */
    default List<VectorSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        throw new UnsupportedOperationException("listByObject is not implemented");
    }

    /**
     * objectType/objectId에 대한 메타데이터를 조회한다.
     * 구현체는 필요한 경우 chunk_index 순으로 첫 번째 레코드를 사용하거나 통합 메타를 반환한다.
     */
    default Map<String, Object> getMetadata(String objectType, String objectId) {
        throw new UnsupportedOperationException("getMetadata is not implemented");
    }
}
