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

package studio.one.base.security.audit.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.domain.entity.LoginFailureLog;
import studio.one.base.security.audit.domain.repository.LoginFailureLogRepository;

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
@Slf4j
public class LoginFailureQueryServiceImpl implements LoginFailureQueryService {

    private final LoginFailureLogRepository repo;

    @Override
    public Page<LoginFailureLog> find(LoginFailQuery q, Pageable pageable) {
        Pageable p = safePageable(pageable);
        log.debug("[LoginFailureQuery] usernameLike={}, ipEquals={}, failureType={}, from={}, to={}",
                q != null ? q.getUsernameLike() : null,
                q != null ? q.getIpEquals() : null,
                q != null ? q.getFailureType() : null,
                q != null ? q.getFrom() : null,
                q != null ? q.getTo() : null);
        return repo.search(q, p);
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
