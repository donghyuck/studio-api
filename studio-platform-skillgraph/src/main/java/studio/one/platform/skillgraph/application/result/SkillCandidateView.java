package studio.one.platform.skillgraph.application.result;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;

public record SkillCandidateView(
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

    public static SkillCandidateView from(SkillCandidate candidate) {
        return from(candidate, null, List.of());
    }

    public static SkillCandidateView from(SkillCandidate candidate, SkillEmbeddingMetadata embeddingMetadata) {
        return from(candidate, null, embeddingMetadata);
    }

    public static SkillCandidateView from(
            SkillCandidate candidate,
            SkillMatchedDictionaryView matchedSkill,
            List<SkillEmbeddingMetadata> embeddings) {
        SkillEmbeddingMetadata latest = embeddings == null || embeddings.isEmpty() ? null : embeddings.get(0);
        SkillCandidateView view = from(candidate, matchedSkill, latest);
        return new SkillCandidateView(
                view.candidateId(), view.sourceChunkId(), view.sourceType(), view.sourceId(), view.term(),
                view.normalizedTerm(), view.searchText(), view.skillType(), view.action(), view.technology(),
                view.target(), view.evidenceText(), view.context(), view.difficulty(), view.extractionMethod(),
                view.confidenceDetail(), view.sourcePosition(), view.normalizationInfo(), view.mappingCandidates(),
                view.reviewStatus(), view.feedback(), view.embedded(), view.embeddingProvider(), view.embeddingModel(),
                view.embeddingDimension(), embeddings == null ? List.of() : List.copyOf(embeddings), view.status(),
                view.confidence(), view.similarityScore(), view.matchedSkill(), view.occurrenceCount(),
                view.matchedSkillId(), view.reviewerNote(), view.createdAt(), view.updatedAt());
    }

    public static SkillCandidateView from(SkillCandidate candidate, SkillMatchedDictionaryView matchedSkill) {
        return from(candidate, matchedSkill, List.of());
    }

    public static SkillCandidateView from(
            SkillCandidate candidate,
            SkillMatchedDictionaryView matchedSkill,
            SkillEmbeddingMetadata embeddingMetadata) {
        Double similarityScore = similarityScore(candidate.reviewerNote());
        if (matchedSkill != null) {
            similarityScore = matchedSkill.similarityScore();
        }
        return new SkillCandidateView(
                candidate.candidateId(),
                candidate.sourceChunkId(),
                candidate.sourceType(),
                candidate.sourceId(),
                candidate.term(),
                candidate.normalizedTerm(),
                candidate.searchText(),
                candidate.skillType(),
                candidate.action(),
                candidate.technology(),
                candidate.target(),
                candidate.evidenceText(),
                candidate.context(),
                candidate.difficulty(),
                candidate.extractionMethod(),
                candidate.confidenceDetail(),
                candidate.sourcePosition(),
                candidate.normalizationInfo(),
                candidate.mappingCandidates(),
                candidate.reviewStatus(),
                candidate.feedback(),
                candidate.embedded() || embeddingMetadata != null,
                embeddingMetadata == null ? null : embeddingMetadata.embeddingProvider(),
                embeddingMetadata == null ? null : embeddingMetadata.embeddingModel(),
                embeddingMetadata == null ? null : embeddingMetadata.embeddingDimension(),
                embeddingMetadata == null ? List.of() : List.of(embeddingMetadata),
                candidate.status(),
                candidate.confidence(),
                similarityScore,
                matchedSkill,
                candidate.occurrenceCount(),
                candidate.matchedSkillId(),
                candidate.reviewerNote(),
                candidate.createdAt(),
                candidate.updatedAt());
    }

    private static Double similarityScore(String reviewerNote) {
        if (reviewerNote == null || !reviewerNote.startsWith("similarity=")) {
            return null;
        }
        try {
            String value = reviewerNote.substring("similarity=".length());
            int delimiter = value.indexOf(';');
            return Double.parseDouble(delimiter < 0 ? value : value.substring(0, delimiter));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
