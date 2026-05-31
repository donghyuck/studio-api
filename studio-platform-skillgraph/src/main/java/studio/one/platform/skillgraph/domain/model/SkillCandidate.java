package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record SkillCandidate(
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
        SkillCandidateStatus status,
        double confidence,
        int occurrenceCount,
        String matchedSkillId,
        String reviewerNote,
        Instant createdAt,
        Instant updatedAt) {

    public SkillCandidate(
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
        this(candidateId, sourceChunkId, sourceType, sourceId, term, normalizedTerm,
                null, null, null, List.of(), null, null, null, null, null, null, null, null, null, null, null,
                false,
                status, confidence, occurrenceCount, matchedSkillId, reviewerNote, createdAt, updatedAt);
    }

    public SkillCandidate {
        candidateId = requireText(candidateId, "candidateId");
        sourceChunkId = normalize(sourceChunkId);
        sourceType = normalize(sourceType);
        sourceId = normalize(sourceId);
        term = requireText(term, "term");
        normalizedTerm = normalizeSkillTerm(normalizedTerm == null ? term : normalizedTerm);
        searchText = normalize(searchText);
        skillType = normalize(skillType);
        action = normalize(action);
        technology = technology == null ? List.of() : technology.stream()
                .map(SkillCandidate::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
        target = normalize(target);
        evidenceText = normalize(evidenceText);
        context = normalize(context);
        difficulty = normalize(difficulty);
        extractionMethod = normalize(extractionMethod);
        confidenceDetail = normalize(confidenceDetail);
        sourcePosition = normalize(sourcePosition);
        normalizationInfo = normalize(normalizationInfo);
        mappingCandidates = normalize(mappingCandidates);
        reviewStatus = normalize(reviewStatus);
        feedback = normalize(feedback);
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
                searchText, skillType, action, technology, target, evidenceText, context, difficulty, extractionMethod,
                confidenceDetail, sourcePosition, normalizationInfo, mappingCandidates, reviewStatus, feedback, embedded,
                nextStatus, confidence, occurrenceCount, matchedSkillId, reviewerNote, createdAt, now);
    }

    public SkillCandidate incrementOccurrence(Instant now) {
        return new SkillCandidate(candidateId, sourceChunkId, sourceType, sourceId, term, normalizedTerm,
                searchText, skillType, action, technology, target, evidenceText, context, difficulty, extractionMethod,
                confidenceDetail, sourcePosition, normalizationInfo, mappingCandidates, reviewStatus, feedback, embedded,
                status, confidence, occurrenceCount + 1, matchedSkillId, reviewerNote, createdAt, now);
    }

    public String embeddingText() {
        return searchText == null || searchText.isBlank() ? term : searchText;
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
