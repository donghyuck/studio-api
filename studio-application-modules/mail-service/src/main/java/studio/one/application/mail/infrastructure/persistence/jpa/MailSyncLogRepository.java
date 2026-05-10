package studio.one.application.mail.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.mail.infrastructure.persistence.jpa.MailSyncLogEntity;

public interface MailSyncLogRepository extends JpaRepository<MailSyncLogEntity, Long> {

    List<MailSyncLogEntity> findTop50ByOrderByStartedAtDesc();
}
