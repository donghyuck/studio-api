package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.result.SkillMatchedDictionaryView;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;

public record SkillCandidateDto(
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

    public static SkillCandidateDto from(SkillCandidateView view) {
        return new SkillCandidateDto(view.candidateId(), view.sourceChunkId(), view.sourceType(), view.sourceId(),
                view.term(), view.normalizedTerm(), view.status(), view.confidence(), view.similarityScore(),
                view.matchedSkill(), view.occurrenceCount(), view.matchedSkillId(), view.reviewerNote(), view.createdAt(),
                view.updatedAt());
    }
}
