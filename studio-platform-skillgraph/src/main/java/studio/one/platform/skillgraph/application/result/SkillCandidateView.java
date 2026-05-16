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
                candidate.occurrenceCount(),
                candidate.matchedSkillId(),
                candidate.reviewerNote(),
                candidate.createdAt(),
                candidate.updatedAt());
    }
}
