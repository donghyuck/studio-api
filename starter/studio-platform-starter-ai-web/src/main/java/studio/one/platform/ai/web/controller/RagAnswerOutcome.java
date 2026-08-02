package studio.one.platform.ai.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Typed, reader-safe outcome for a completed RAG turn.
 */
public record RagAnswerOutcome(
        Type type,
        Stage stage,
        ReasonCode reasonCode,
        int retrievedResultCount,
        int acceptedResultCount,
        int packedEvidenceCount,
        Set<Integer> usedEvidenceIndexes,
        String citationValidationStatus,
        String policyValidationStatus,
        int validationUnitCount,
        int citedValidationUnitCount,
        boolean partial,
        int originalValidationUnitCount,
        int omittedValidationUnitCount) {

    public RagAnswerOutcome(
            Type type,
            Stage stage,
            ReasonCode reasonCode,
            int retrievedResultCount,
            int acceptedResultCount,
            int packedEvidenceCount,
            Set<Integer> usedEvidenceIndexes,
            String citationValidationStatus,
            String policyValidationStatus,
            int validationUnitCount,
            int citedValidationUnitCount) {
        this(
                type,
                stage,
                reasonCode,
                retrievedResultCount,
                acceptedResultCount,
                packedEvidenceCount,
                usedEvidenceIndexes,
                citationValidationStatus,
                policyValidationStatus,
                validationUnitCount,
                citedValidationUnitCount,
                false,
                validationUnitCount,
                0);
    }

    public RagAnswerOutcome {
        type = type == null ? Type.ABSTAINED : type;
        stage = stage == null ? Stage.NONE : stage;
        reasonCode = reasonCode == null ? ReasonCode.NONE : reasonCode;
        usedEvidenceIndexes = usedEvidenceIndexes == null ? Set.of() : Set.copyOf(usedEvidenceIndexes);
        citationValidationStatus = safe(citationValidationStatus);
        policyValidationStatus = safe(policyValidationStatus);
        retrievedResultCount = Math.max(0, retrievedResultCount);
        acceptedResultCount = Math.max(0, acceptedResultCount);
        packedEvidenceCount = Math.max(0, packedEvidenceCount);
        validationUnitCount = Math.max(0, validationUnitCount);
        citedValidationUnitCount = Math.max(0, citedValidationUnitCount);
        originalValidationUnitCount = Math.max(validationUnitCount, originalValidationUnitCount);
        omittedValidationUnitCount = Math.max(0, omittedValidationUnitCount);
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", type.name());
        metadata.put("stage", stage.name());
        metadata.put("reasonCode", reasonCode.name());
        metadata.put("retrievedResultCount", retrievedResultCount);
        metadata.put("acceptedResultCount", acceptedResultCount);
        metadata.put("packedEvidenceCount", packedEvidenceCount);
        metadata.put("usedEvidenceIndexes", usedEvidenceIndexes.stream().sorted().toList());
        metadata.put("citationValidationStatus", citationValidationStatus);
        metadata.put("policyValidationStatus", policyValidationStatus);
        metadata.put("validationUnitCount", validationUnitCount);
        metadata.put("citedValidationUnitCount", citedValidationUnitCount);
        metadata.put("partial", partial);
        metadata.put("originalValidationUnitCount", originalValidationUnitCount);
        metadata.put("omittedValidationUnitCount", omittedValidationUnitCount);
        return Map.copyOf(metadata);
    }

    public enum Type {
        ANSWERED,
        EVIDENCE_ONLY,
        ABSTAINED
    }

    public enum Stage {
        NONE,
        RETRIEVAL,
        PACKING,
        VALIDATION,
        GENERATION
    }

    public enum ReasonCode {
        NONE,
        NO_RETRIEVAL_RESULTS,
        NO_PACKED_EVIDENCE,
        EMPTY_DRAFT,
        MISSING_CITATION,
        OUT_OF_RANGE_CITATION,
        MISSING_UNIT_CITATION,
        MISSING_COMPARISON_SOURCE_CITATION,
        INSUFFICIENT_SOURCE_COVERAGE,
        NO_USABLE_SOURCE_SPAN,
        NO_MATCHING_TARGET
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
