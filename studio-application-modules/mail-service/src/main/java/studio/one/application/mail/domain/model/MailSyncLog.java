package studio.one.application.mail.domain.model;

import java.time.Instant;

public interface MailSyncLog {

    long getLogId();
    void setLogId(long logId);

    Instant getStartedAt();
    void setStartedAt(Instant startedAt);

    Instant getFinishedAt();
    void setFinishedAt(Instant finishedAt);

    int getProcessed();
    void setProcessed(int processed);

    int getSucceeded();
    void setSucceeded(int succeeded);

    int getFailed();
    void setFailed(int failed);

    String getStatus();
    void setStatus(String status);

    String getMessage();
    void setMessage(String message);

    String getTriggeredBy();
    void setTriggeredBy(String triggeredBy);
}
