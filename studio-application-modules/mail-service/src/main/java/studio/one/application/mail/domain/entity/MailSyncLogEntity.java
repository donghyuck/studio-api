package studio.one.application.mail.domain.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.NoArgsConstructor;
import studio.one.application.mail.domain.model.MailSyncLog;

@Data
@NoArgsConstructor
@Entity
@Table(name = "TB_APPLICATION_MAIL_SYNC_LOG")
@EntityListeners(AuditingEntityListener.class)
public class MailSyncLogEntity implements MailSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID", nullable = false)
    private long logId;

    @Column(name = "STARTED_AT", nullable = false)
    private Instant startedAt;

    @Column(name = "FINISHED_AT")
    private Instant finishedAt;

    @Column(name = "PROCESSED")
    private int processed;

    @Column(name = "SUCCEEDED")
    private int succeeded;

    @Column(name = "FAILED")
    private int failed;

    @Column(name = "STATUS", length = 50)
    private String status;

    @Column(name = "MESSAGE", length = 1000)
    private String message;

    @Column(name = "TRIGGERED_BY", length = 100)
    private String triggeredBy;
}
