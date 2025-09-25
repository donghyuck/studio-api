package studio.echo.base.security.audit;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginFailureLogRepository extends JpaRepository<LoginFailureLog, Long> {

    long deleteByOccurredAtBefore(Instant cutoff);

    long countByUsernameAndOccurredAtAfter(String username, Instant since);

}
