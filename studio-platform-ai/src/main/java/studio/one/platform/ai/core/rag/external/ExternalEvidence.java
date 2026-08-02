package studio.one.platform.ai.core.rag.external;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public record ExternalEvidence(
        String evidenceId,
        ExternalEvidenceSourceType sourceType,
        String title,
        String publisher,
        URI canonicalUri,
        LocalDate publishedDate,
        LocalDate effectiveDate,
        Instant retrievedAt,
        String exactText,
        String contentHash,
        double score,
        Map<String, Object> metadata) {

    public ExternalEvidence {
        evidenceId = requireText(evidenceId, "evidenceId");
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        title = requireText(title, "title");
        publisher = requireText(publisher, "publisher");
        canonicalUri = Objects.requireNonNull(canonicalUri, "canonicalUri");
        retrievedAt = Objects.requireNonNull(retrievedAt, "retrievedAt");
        exactText = requireText(exactText, "exactText");
        contentHash = requireText(contentHash, "contentHash");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
