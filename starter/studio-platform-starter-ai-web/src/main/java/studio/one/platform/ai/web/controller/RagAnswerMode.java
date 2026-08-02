package studio.one.platform.ai.web.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controls how far a RAG answer may go beyond directly stated source facts.
 */
public enum RagAnswerMode {
    STRICT_GROUNDED(0),
    GROUNDED_INFERENCE(1);

    private final int permissiveness;

    RagAnswerMode(int permissiveness) {
        this.permissiveness = permissiveness;
    }

    public boolean isMorePermissiveThan(RagAnswerMode other) {
        return permissiveness > other.permissiveness;
    }

    public static RagAnswerMode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported RAG answerMode: " + value,
                    ex);
        }
    }
}
