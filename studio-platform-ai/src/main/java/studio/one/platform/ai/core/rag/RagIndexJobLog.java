package studio.one.platform.ai.core.rag;

import java.time.Instant;
import java.util.Objects;

public final class RagIndexJobLog {

    private final String logId;
    private final String jobId;
    private final RagIndexJobLogLevel level;
    private final RagIndexJobStep step;
    private final RagIndexJobLogCode code;
    private final String message;
    private final String detail;
    private final Instant createdAt;

    public RagIndexJobLog(
            String logId,
            String jobId,
            RagIndexJobLogLevel level,
            RagIndexJobStep step,
            RagIndexJobLogCode code,
            String message,
            String detail,
            Instant createdAt
    ) {
                logId = requireText(logId, "logId");
                jobId = requireText(jobId, "jobId");
                level = level == null ? RagIndexJobLogLevel.INFO : level;
                code = code == null ? RagIndexJobLogCode.UNKNOWN_ERROR : code;
                message = normalize(message);
                detail = normalize(detail);
                createdAt = createdAt == null ? Instant.now() : createdAt;
        
        this.logId = logId;
        this.jobId = jobId;
        this.level = level;
        this.step = step;
        this.code = code;
        this.message = message;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public String logId() {
        return logId;
    }

    public String jobId() {
        return jobId;
    }

    public RagIndexJobLogLevel level() {
        return level;
    }

    public RagIndexJobStep step() {
        return step;
    }

    public RagIndexJobLogCode code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String detail() {
        return detail;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagIndexJobLog)) {
            return false;
        }
        RagIndexJobLog that = (RagIndexJobLog) o;
        return java.util.Objects.equals(logId, that.logId)
                && java.util.Objects.equals(jobId, that.jobId)
                && java.util.Objects.equals(level, that.level)
                && java.util.Objects.equals(step, that.step)
                && java.util.Objects.equals(code, that.code)
                && java.util.Objects.equals(message, that.message)
                && java.util.Objects.equals(detail, that.detail)
                && java.util.Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(logId, jobId, level, step, code, message, detail, createdAt);
    }

    @Override
    public String toString() {
        return "RagIndexJobLog[" +
                "logId=" + logId + ", " +
                "jobId=" + jobId + ", " +
                "level=" + level + ", " +
                "step=" + step + ", " +
                "code=" + code + ", " +
                "message=" + message + ", " +
                "detail=" + detail + ", " +
                "createdAt=" + createdAt +
                "]";
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        return Objects.requireNonNull(normalized, name + " must not be blank");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
