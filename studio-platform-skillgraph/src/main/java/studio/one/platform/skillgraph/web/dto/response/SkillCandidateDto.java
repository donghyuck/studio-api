package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.result.SkillMatchedDictionaryView;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;

public record SkillCandidateDto(
        String candidateId,
        String sourceChunkId,
        String sourceType,
        String sourceId,
        String term,
        String normalizedTerm,
        String searchText,
        String skillType,
        String action,
        List<String> technology,
        String target,
        String evidenceText,
        String context,
        String difficulty,
        String extractionMethod,
        String confidenceDetail,
        String sourcePosition,
        String normalizationInfo,
        String mappingCandidates,
        String reviewStatus,
        String feedback,
        boolean embedded,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        List<SkillEmbeddingMetadata> embeddings,
        SkillCandidateStatus status,
        double confidence,
        Double similarityScore,
        SkillMatchedDictionaryView matchedSkill,
        int occurrenceCount,
        String matchedSkillId,
        String reviewerNote,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillCandidateDto from(SkillCandidateView view) {
        return new SkillCandidateDto(view.candidateId(), view.sourceChunkId(), view.sourceType(), view.sourceId(),
                view.term(), view.normalizedTerm(), view.searchText(), view.skillType(), view.action(),
                view.technology(), view.target(), view.evidenceText(), view.context(), view.difficulty(),
                view.extractionMethod(), view.confidenceDetail(), view.sourcePosition(), view.normalizationInfo(),
                view.mappingCandidates(), view.reviewStatus(), view.feedback(), view.embedded(),
                view.embeddingProvider(), view.embeddingModel(), view.embeddingDimension(), view.embeddings(),
                view.status(), view.confidence(), view.similarityScore(),
                view.matchedSkill(), view.occurrenceCount(), view.matchedSkillId(), view.reviewerNote(), view.createdAt(),
                view.updatedAt());
    }
}
