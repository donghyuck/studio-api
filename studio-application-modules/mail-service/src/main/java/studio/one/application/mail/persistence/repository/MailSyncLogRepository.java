package studio.one.application.mail.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.mail.domain.entity.MailSyncLogEntity;

public interface MailSyncLogRepository extends JpaRepository<MailSyncLogEntity, Long> {

    List<MailSyncLogEntity> findTop50ByOrderByStartedAtDesc();
}
