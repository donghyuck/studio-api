package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;
import java.util.Locale;

public record SkillCandidate(
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

    public SkillCandidate {
        candidateId = requireText(candidateId, "candidateId");
        sourceChunkId = normalize(sourceChunkId);
        sourceType = normalize(sourceType);
        sourceId = normalize(sourceId);
        term = requireText(term, "term");
        normalizedTerm = normalizeSkillTerm(normalizedTerm == null ? term : normalizedTerm);
        status = status == null ? SkillCandidateStatus.PENDING : status;
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        occurrenceCount = Math.max(1, occurrenceCount);
        matchedSkillId = normalize(matchedSkillId);
        reviewerNote = normalize(reviewerNote);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public SkillCandidate withStatus(SkillCandidateStatus nextStatus, String matchedSkillId, String reviewerNote, Instant now) {
        return new SkillCandidate(candidateId, sourceChunkId, sourceType, sourceId, term, normalizedTerm,
                nextStatus, confidence, occurrenceCount, matchedSkillId, reviewerNote, createdAt, now);
    }

    public SkillCandidate incrementOccurrence(Instant now) {
        return new SkillCandidate(candidateId, sourceChunkId, sourceType, sourceId, term, normalizedTerm,
                status, confidence, occurrenceCount + 1, matchedSkillId, reviewerNote, createdAt, now);
    }

    public static String normalizeSkillTerm(String value) {
        String normalized = requireText(value, "term")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
