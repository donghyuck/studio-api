package studio.one.base.security.audit.domain.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import studio.one.base.security.audit.domain.entity.LoginFailureLog;

public interface LoginFailureLogRepository
    extends JpaRepository<LoginFailureLog, Long>, JpaSpecificationExecutor<LoginFailureLog> {

  long deleteByOccurredAtBefore(Instant cutoff);

  long countByUsernameAndOccurredAtAfter(String username, Instant since);

}
