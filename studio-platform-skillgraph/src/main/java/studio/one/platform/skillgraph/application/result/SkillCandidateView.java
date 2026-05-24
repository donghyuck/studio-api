package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateView(
        String candidateId,
        String sourceChunkId,
        String sourceType,
        String sourceId,
        String term,
        String normalizedTerm,
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
        return new SkillCandidateView(
                candidate.candidateId(),
                candidate.sourceChunkId(),
                candidate.sourceType(),
                candidate.sourceId(),
                candidate.term(),
                candidate.normalizedTerm(),
                candidate.status(),
                candidate.confidence(),
                similarityScore(candidate.reviewerNote()),
                null,
                candidate.occurrenceCount(),
                candidate.matchedSkillId(),
                candidate.reviewerNote(),
                candidate.createdAt(),
                candidate.updatedAt());
    }

    public static SkillCandidateView from(SkillCandidate candidate, SkillMatchedDictionaryView matchedSkill) {
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
