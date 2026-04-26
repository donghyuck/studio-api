package studio.one.platform.ai.web.dto;

import java.time.Instant;

import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

public record RagIndexJobLogDto(
        String logId,
        String jobId,
        RagIndexJobLogLevel level,
        RagIndexJobStep step,
        RagIndexJobLogCode code,
        String message,
        String detail,
        Instant createdAt) {

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
