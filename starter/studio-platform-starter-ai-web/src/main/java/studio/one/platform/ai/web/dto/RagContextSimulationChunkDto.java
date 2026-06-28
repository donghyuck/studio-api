package studio.one.platform.ai.web.dto;

import java.util.List;
import java.util.Map;

public record RagContextSimulationChunkDto(
        String documentId,
        String content,
        Map<String, Object> metadata,
        Double score,
        Integer rank,
        String chunkId,
        Integer chunkIndex,
        String objectType,
        String objectId,
        Integer tokenCount,
        Boolean included,
        String exclusionReason,
        Integer cumulativeTokenCount,
        String tokenizerProvider,
        String tokenizerEncoding,
        String embeddingModel,
        List<String> warnings) {
}
