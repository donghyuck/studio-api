package studio.one.base.security.audit.domain.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.service.LoginFailQuery;

public interface LoginFailureLogRepository {

  LoginFailureLog save(LoginFailureLog log);

  long deleteOlderThan(Instant cutoff);

  long countByUsernameSince(String username, Instant since);

  Page<LoginFailureLog> search(LoginFailQuery query, Pageable pageable);
}
