package studio.one.platform.chunking.artifact;

import java.util.Optional;

public interface ChunkSetStore {

    ChunkSet save(ChunkSet chunkSet);

    Optional<ChunkSet> findById(String chunkSetId);

    Optional<ChunkSet> findLatest(String objectType, String objectId, String documentId, String sourceRevisionId);

    default void invalidate(String chunkSetId) {
    }

    static ChunkSetStore noop() {
        return new ChunkSetStore() {
            @Override
            public ChunkSet save(ChunkSet chunkSet) {
                return chunkSet;
            }

            @Override
            public Optional<ChunkSet> findById(String chunkSetId) {
                return Optional.empty();
            }

            @Override
            public Optional<ChunkSet> findLatest(
                    String objectType, String objectId, String documentId, String sourceRevisionId) {
                return Optional.empty();
            }
        };
    }
}
