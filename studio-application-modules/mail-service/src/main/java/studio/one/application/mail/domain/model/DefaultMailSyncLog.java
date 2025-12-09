package studio.one.application.mail.domain.model;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DefaultMailSyncLog implements MailSyncLog {
    private long logId;
    private Instant startedAt;
    private Instant finishedAt;
    private int processed;
    private int succeeded;
    private int failed;
    private String status;
    private String message;
    private String triggeredBy;
}
