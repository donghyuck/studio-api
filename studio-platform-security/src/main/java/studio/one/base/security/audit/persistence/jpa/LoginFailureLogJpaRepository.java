package studio.one.base.security.audit.persistence.jpa;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.persistence.LoginFailureLogRepository;
import studio.one.base.security.audit.service.LoginFailQuery;

@Repository
public interface LoginFailureLogJpaRepository 
    extends JpaRepository<LoginFailureLog, Long>, JpaSpecificationExecutor<LoginFailureLog>, LoginFailureLogRepository {

    long deleteByOccurredAtBefore(Instant cutoff);

    long countByUsernameAndOccurredAtAfter(String username, Instant since);

    @Override
    default long deleteOlderThan(Instant cutoff) {
        return deleteByOccurredAtBefore(cutoff);
    }

    @Override
    default long countByUsernameSince(String username, Instant since) {
        return countByUsernameAndOccurredAtAfter(username, since);
    }

    @Override
    default Page<LoginFailureLog> search(LoginFailQuery query, Pageable pageable) {
        Specification<LoginFailureLog> spec = LoginFailureSpecifications.from(query);
        return findAll(spec, pageable);
    }
}
