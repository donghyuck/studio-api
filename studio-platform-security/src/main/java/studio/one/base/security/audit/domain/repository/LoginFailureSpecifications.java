package studio.one.base.security.audit.domain.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.service.LoginFailQuery;

final class LoginFailureSpecifications {

    private LoginFailureSpecifications() {
    }

    static Specification<LoginFailureLog> from(LoginFailQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                setUsernameLike(query.getUsernameLike(), predicates, root, cb);
                setIpEquals(query.getIpEquals(), predicates, root, cb);
                setFailureType(query.getFailureType(), predicates, root, cb);
                setFrom(query.getFrom(), predicates, root, cb);
                setTo(query.getTo(), predicates, root, cb);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void setUsernameLike(String usernameLike, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (usernameLike == null || usernameLike.isBlank()) {
            return;
        }
        ps.add(cb.like(cb.lower(root.get("username")), "%" + usernameLike.toLowerCase() + "%"));
    }

    private static void setIpEquals(String ipEquals, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (ipEquals == null || ipEquals.isBlank()) {
            return;
        }
        ps.add(cb.equal(root.get("remoteIp"), ipEquals));
    }

    private static void setFailureType(String failureType, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (failureType == null || failureType.isBlank()) {
            return;
        }
        ps.add(cb.equal(root.get("failureType"), failureType));
    }

    private static void setFrom(OffsetDateTime from, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (from == null) {
            return;
        }
        Instant instant = from.toInstant();
        ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), instant));
    }

    private static void setTo(OffsetDateTime to, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (to == null) {
            return;
        }
        Instant instant = to.toInstant();
        ps.add(cb.lessThan(root.get("occurredAt"), instant));
    }
}
