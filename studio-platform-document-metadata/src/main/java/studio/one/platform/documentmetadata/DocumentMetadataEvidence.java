package studio.one.platform.documentmetadata;

import java.util.Objects;

public record DocumentMetadataEvidence(
        String exactText,
        String sourceRef,
        String blockId,
        Integer page,
        Integer slide,
        String section,
        Integer startOffset,
        Integer endOffset) {

    public static final int MAX_TEXT_LENGTH = 500;

    public DocumentMetadataEvidence {
        exactText = normalize(exactText);
        if (exactText == null) {
            throw new IllegalArgumentException("exactText is required");
        }
        if (exactText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("exactText exceeds " + MAX_TEXT_LENGTH + " characters");
        }
        sourceRef = normalize(sourceRef);
        blockId = normalize(blockId);
        section = normalize(section);
        if (startOffset != null && startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be non-negative");
        }
        if (endOffset != null && endOffset < 0) {
            throw new IllegalArgumentException("endOffset must be non-negative");
        }
        if (startOffset != null && endOffset != null && endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset must be greater than or equal to startOffset");
        }
    }

    public boolean isExactSubstringOf(String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        if (startOffset != null && endOffset != null && endOffset <= normalizedText.length()) {
            return exactText.equals(normalizedText.substring(startOffset, endOffset));
        }
        return normalizedText.contains(exactText);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
