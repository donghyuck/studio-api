package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillExtractionResult(
        String sourceChunkId,
        int extractedCount,
        List<SkillCandidateView> candidates) {
}
