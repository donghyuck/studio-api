package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.List;

public record DocumentQuestionSuggestionsResponseDto(
        String contractVersion,
        Instant generatedAt,
        Basis basis,
        Availability availability,
        List<Suggestion> suggestions,
        Policy policy) {

    public DocumentQuestionSuggestionsResponseDto {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public record Basis(
            String objectType,
            String objectId,
            String documentId,
            String revisionId,
            String sourceContentHash,
            String chunkSetId) {
    }

    public record Availability(
            Status status,
            List<String> reasonCodes) {

        public Availability {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    public record Suggestion(
            String id,
            String query,
            Type type,
            List<String> keywords,
            Source source) {

        public Suggestion {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }

    public record Policy(
            String version,
            String fingerprint,
            int maxSuggestions) {
    }

    public enum Status {
        AVAILABLE,
        NOT_READY,
        NO_SIGNALS
    }

    public enum Type {
        CRITICAL_QUESTION,
        KEYWORD_EXPLANATION,
        KEYWORD_RELATION,
        KEYWORD_SUMMARY
    }

    public enum Source {
        IDEA_BLOCK_QUESTION,
        DOCUMENT_KEYWORDS,
        KEY_POINTS,
        CHUNK_KEYWORDS
    }
}
