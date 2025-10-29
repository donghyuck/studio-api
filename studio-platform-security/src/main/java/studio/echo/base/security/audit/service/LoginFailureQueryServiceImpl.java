/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file LoginFailureQueryServiceImpl.java
 *      @date 2025
 *
 */

package studio.echo.base.security.audit.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.echo.base.security.audit.domain.entity.LoginFailureLog;
import studio.echo.base.security.audit.domain.repository.LoginFailureLogRepository;

/**
 * 
 * @author  donghyuck, son
 * @since 2025-10-29
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-10-29  donghyuck, son: 최초 생성.
 * </pre>
 */

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginFailureQueryServiceImpl implements LoginFailureQueryService {

    private final LoginFailureLogRepository repo;

    @Override
    public Page<LoginFailureLog> find(LoginFailQuery q, Pageable pageable) {
        Pageable p = safePageable(pageable);
        Specification<LoginFailureLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null) {
                // 날짜 범위
                OffsetDateTime from = q.getFrom();
                OffsetDateTime to = q.getTo();
                if (from != null)
                    ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.toInstant()));
                if (to != null)
                    ps.add(cb.lessThan(root.get("occurredAt"), to.toInstant())); 
                // username LIKE
                setUsernameLike(q.getUsernameLike(), ps, root, cb); 
                // ip equals (엔티티 컬럼명이 remoteIp 라고 가정)
                setIpEquals(q.getIpEquals(), ps, root, cb); 
                // failureType equals
                setFailureType( q.getFailureType(), ps, root, cb);
            }

            return cb.and(ps.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, p);
    }

    private void setUsernameLike(String usernameLike, List<Predicate> ps, Root<LoginFailureLog> root,
            CriteriaBuilder cb) {
        if (usernameLike != null && !usernameLike.isBlank())
            ps.add(cb.like(cb.lower(root.get("username")), "%" + usernameLike.trim().toLowerCase() + "%"));
    }

    private void setIpEquals(String ipEquals, List<Predicate> ps, Root<LoginFailureLog> root, CriteriaBuilder cb) {
        if (ipEquals != null && !ipEquals.isBlank()) {
            ps.add(cb.equal(root.get("remoteIp"), ipEquals.trim()));
        }
    }

    private void setFailureType(String failureType, List<Predicate> ps, Root<LoginFailureLog> root, CriteriaBuilder cb) {
        if (failureType != null && !failureType.isBlank()) {
            ps.add(cb.equal(root.get("failureType"), failureType.trim()));
        }
    }    

    private Pageable safePageable(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Order.desc("occurredAt"));
        if (pageable == null) {
            return PageRequest.of(0, 15, defaultSort);
        }
        Sort sort = pageable.getSort().isUnsorted() ? defaultSort : pageable.getSort();
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 200);
        int page = Math.max(pageable.getPageNumber(), 0);
        return PageRequest.of(page, size, sort);
    }
}
