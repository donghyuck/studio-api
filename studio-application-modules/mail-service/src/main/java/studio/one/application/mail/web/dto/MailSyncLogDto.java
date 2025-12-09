package studio.one.application.mail.web.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;
import studio.one.application.mail.domain.model.MailSyncLog;

@Value
@Builder
public class MailSyncLogDto {
    long logId;
    Instant startedAt;
    Instant finishedAt;
    int processed;
    int succeeded;
    int failed;
    String status;
    String message;
    String triggeredBy;

    public static MailSyncLogDto from(MailSyncLog log) {
        return MailSyncLogDto.builder()
                .logId(log.getLogId())
                .startedAt(log.getStartedAt())
                .finishedAt(log.getFinishedAt())
                .processed(log.getProcessed())
                .succeeded(log.getSucceeded())
                .failed(log.getFailed())
                .status(log.getStatus())
                .message(log.getMessage())
                .triggeredBy(log.getTriggeredBy())
                .build();
    }
}
