package studio.one.platform.ai.web.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controls which evidence sources may be used for a RAG answer.
 */
public enum RagSourceScope {
    DOCUMENT_ONLY(0),
    DOCUMENT_AND_OFFICIAL_EXTERNAL(1);

    private final int breadth;

    RagSourceScope(int breadth) {
        this.breadth = breadth;
    }

    public boolean isBroaderThan(RagSourceScope other) {
        return breadth > other.breadth;
    }

    public static RagSourceScope parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported RAG sourceScope: " + value,
                    ex);
        }
    }
}
