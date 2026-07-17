package studio.one.platform.chunking.artifact;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChunkSetStore implements ChunkSetStore {

    private final Map<String, ChunkSet> values = new ConcurrentHashMap<>();

    @Override
    public ChunkSet save(ChunkSet chunkSet) {
        values.put(chunkSet.chunkSetId(), chunkSet);
        return chunkSet;
    }

    @Override
    public Optional<ChunkSet> findById(String chunkSetId) {
        return Optional.ofNullable(values.get(chunkSetId));
    }

    @Override
    public Optional<ChunkSet> findLatest(
            String objectType, String objectId, String documentId, String sourceRevisionId) {
        return values.values().stream()
                .filter(value -> value.objectType().equals(objectType))
                .filter(value -> value.objectId().equals(objectId))
                .filter(value -> value.documentId().equals(documentId))
                .filter(value -> value.sourceRevisionId().equals(sourceRevisionId))
                .max(Comparator.comparing(ChunkSet::updatedAt));
    }

    @Override
    public void invalidate(String chunkSetId) {
        values.computeIfPresent(chunkSetId, (ignored, source) -> new ChunkSet(
                source.chunkSetId(), source.objectType(), source.objectId(), source.documentId(),
                source.sourceRevisionId(), source.sourceContentHash(), source.strategy(), source.strategyHash(),
                source.chunkUnit(), source.maxSize(), source.overlap(), ChunkSetStatus.INVALIDATED,
                source.qualityStatus(), source.qualityIssues(), source.metadata(), source.items(),
                source.createdAt(), Instant.now()));
    }
}
