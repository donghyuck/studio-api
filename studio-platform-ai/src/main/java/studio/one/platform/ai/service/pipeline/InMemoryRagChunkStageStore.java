package studio.one.platform.ai.service.pipeline;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryRagChunkStageStore implements RagChunkStageStore {

    private final ConcurrentMap<Key, List<RagChunkStage>> chunks = new ConcurrentHashMap<>();

    @Override
    public void replace(String objectType, String objectId, String documentId, List<RagChunkStage> chunks) {
        this.chunks.put(new Key(objectType, objectId, documentId), List.copyOf(chunks));
    }

    @Override
    public List<RagChunkStage> findByObject(String objectType, String objectId, String documentId) {
        return chunks.getOrDefault(new Key(objectType, objectId, documentId), List.of()).stream()
                .sorted(Comparator.comparingInt(RagChunkStage::chunkIndex))
                .toList();
    }

    @Override
    public void deleteByObject(String objectType, String objectId, String documentId) {
        chunks.remove(new Key(objectType, objectId, documentId));
    }

    private record Key(String objectType, String objectId, String documentId) {
        private Key {
            objectType = normalize(objectType);
            objectId = normalize(objectId);
            documentId = normalize(documentId);
        }

        private static String normalize(String value) {
            return Objects.requireNonNullElse(value, "");
        }
    }
}
