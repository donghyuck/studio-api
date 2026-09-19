package studio.one.platform.ai.web.service;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

/** Expands candidates only inside the already-authorized object scope supplied by the caller. */
public final class RagContextCandidateService {
    private static final Logger log = LoggerFactory.getLogger(RagContextCandidateService.class);
    private final RagPipelineService pipeline;
    private final int multiplier;
    private final int maxCandidates;

    public RagContextCandidateService(RagPipelineService pipeline, int multiplier, int maxCandidates) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.multiplier = Math.max(1, multiplier);
        this.maxCandidates = Math.max(1, maxCandidates);
    }

    public List<RagSearchResult> expand(List<RagSearchResult> original, String objectType, String objectId,
            int topK, boolean alreadyExpanded, boolean supportsExpansion) {
        if (!supportsExpansion || alreadyExpanded || objectType == null || objectId == null
                || objectType.isBlank() || objectId.isBlank()) {
            return original;
        }
        int limit = candidateLimit(topK);
        try {
            List<RagSearchResult> candidates = pipeline.listByObject(objectType, objectId, limit);
            return candidates == null || candidates.isEmpty() ? original : candidates;
        } catch (RuntimeException ex) {
            log.warn("RAG context expansion candidate fetch failed: objectType={}, errorType={}",
                    objectType, ex.getClass().getSimpleName());
            return original;
        }
    }

    public int candidateLimit(int topK) {
        return (int) Math.min(maxCandidates, (long) Math.max(topK, 1) * multiplier);
    }
}
