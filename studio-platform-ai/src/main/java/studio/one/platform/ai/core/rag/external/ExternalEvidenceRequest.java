package studio.one.platform.ai.core.rag.external;

import java.time.LocalDate;

public record ExternalEvidenceRequest(
        String query,
        String jurisdiction,
        LocalDate asOfDate,
        String language,
        int maxResults) {

    public ExternalEvidenceRequest {
        query = normalize(query);
        jurisdiction = normalize(jurisdiction);
        language = normalize(language);
        maxResults = Math.max(1, Math.min(maxResults, 20));
        if (query == null) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
