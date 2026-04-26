package studio.one.platform.ai.core.rag;

import java.time.Instant;
import java.util.Objects;

public record RagIndexJobLog(
        String logId,
        String jobId,
        RagIndexJobLogLevel level,
        RagIndexJobStep step,
        RagIndexJobLogCode code,
        String message,
        String detail,
        Instant createdAt) {

    public RagIndexJobLog {
        logId = requireText(logId, "logId");
        jobId = requireText(jobId, "jobId");
        level = level == null ? RagIndexJobLogLevel.INFO : level;
        code = code == null ? RagIndexJobLogCode.UNKNOWN_ERROR : code;
        message = normalize(message);
        detail = normalize(detail);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        return Objects.requireNonNull(normalized, name + " must not be blank");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
