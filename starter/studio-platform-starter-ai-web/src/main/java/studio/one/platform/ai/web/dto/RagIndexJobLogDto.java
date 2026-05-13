package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public class RagIndexJobLogDto {

    private final String logId;

    private final String jobId;

    private final RagIndexJobLogLevel level;

    private final RagIndexJobStep step;

    private final RagIndexJobLogCode code;

    private final String message;

    private final String detail;

    private final Instant createdAt;

    @JsonCreator
    public RagIndexJobLogDto(
            @JsonProperty("logId") String logId,
            @JsonProperty("jobId") String jobId,
            @JsonProperty("level") RagIndexJobLogLevel level,
            @JsonProperty("step") RagIndexJobStep step,
            @JsonProperty("code") RagIndexJobLogCode code,
            @JsonProperty("message") String message,
            @JsonProperty("detail") String detail,
            @JsonProperty("createdAt") Instant createdAt
    ) {
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

    public String getLogId() {
        return logId;
    }

    public String jobId() {
        return jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public RagIndexJobLogLevel level() {
        return level;
    }

    public RagIndexJobLogLevel getLevel() {
        return level;
    }

    public RagIndexJobStep step() {
        return step;
    }

    public RagIndexJobStep getStep() {
        return step;
    }

    public RagIndexJobLogCode code() {
        return code;
    }

    public RagIndexJobLogCode getCode() {
        return code;
    }

    public String message() {
        return message;
    }

    public String getMessage() {
        return message;
    }

    public String detail() {
        return detail;
    }

    public String getDetail() {
        return detail;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

public static RagIndexJobLogDto from(RagIndexJobLog log) {
        return new RagIndexJobLogDto(
                log.logId(),
                log.jobId(),
                log.level(),
                log.step(),
                log.code(),
                log.message(),
                log.detail(),
                log.createdAt());
    }

}