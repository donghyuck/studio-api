package studio.one.base.security.audit.domain.port;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.security.audit.domain.model.LoginFailureLog;
import studio.one.base.security.audit.application.command.LoginFailQuery;

public interface LoginFailureLogRepository {

  LoginFailureLog save(LoginFailureLog log);

  long deleteOlderThan(Instant cutoff);

  long countByUsernameSince(String username, Instant since);

  Page<LoginFailureLog> search(LoginFailQuery query, Pageable pageable);
}
