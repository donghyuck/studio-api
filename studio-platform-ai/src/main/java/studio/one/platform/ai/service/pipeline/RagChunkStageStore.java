package studio.one.platform.ai.service.pipeline;

import java.util.List;
import java.util.Objects;

public interface RagChunkStageStore {

    RagChunkStageStore NOOP = new RagChunkStageStore() {
    };

    static RagChunkStageStore noop() {
        return NOOP;
    }

    default void replace(String objectType, String objectId, String documentId, List<RagChunkStage> chunks) {
        Objects.requireNonNull(chunks, "chunks");
    }

    default List<RagChunkStage> findByObject(String objectType, String objectId, String documentId) {
        return List.of();
    }

    default List<RagChunkStage> findIndexedByObject(String objectType, String objectId, String revisionId) {
        return List.of();
    }

    default void deleteByObject(String objectType, String objectId, String documentId) {
    }
}
