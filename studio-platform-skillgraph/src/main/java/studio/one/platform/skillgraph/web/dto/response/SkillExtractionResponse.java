package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillExtractionResult;

public record SkillExtractionResponse(
        String sourceChunkId,
        int extractedCount,
        List<SkillCandidateDto> candidates) {

    public static SkillExtractionResponse from(SkillExtractionResult result) {
        return new SkillExtractionResponse(result.sourceChunkId(), result.extractedCount(),
                result.candidates().stream().map(SkillCandidateDto::from).toList());
    }
}
