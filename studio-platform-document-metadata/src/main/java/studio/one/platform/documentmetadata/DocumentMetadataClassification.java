package studio.one.platform.documentmetadata;

public record DocumentMetadataClassification(
        String requestedProcessingProfile,
        String effectiveProcessingProfile,
        DocumentSemanticTypeSelection requestedSemanticType,
        DocumentSemanticType detectedSemanticType,
        DocumentSemanticType effectiveSemanticType,
        String subject,
        double confidence,
        String classifierVersion,
        String requestedBlockifyType,
        String detectedBlockifyType,
        Double blockifyConfidence,
        String blockifySchemaVersion) {

    public DocumentMetadataClassification {
        requestedSemanticType = requestedSemanticType == null
                ? DocumentSemanticTypeSelection.AUTO : requestedSemanticType;
        detectedSemanticType = detectedSemanticType == null
                ? DocumentSemanticType.UNKNOWN : detectedSemanticType;
        DocumentSemanticType explicit = requestedSemanticType.explicitType();
        effectiveSemanticType = explicit == null ? detectedSemanticType : explicit;
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        subject = normalize(subject);
        classifierVersion = normalize(classifierVersion);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
