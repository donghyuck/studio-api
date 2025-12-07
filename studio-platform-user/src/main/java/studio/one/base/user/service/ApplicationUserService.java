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
 *      @file ApplicationUserService.java
 *      @date 2025
 *
 */

package studio.one.base.user.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.ServiceNames;

/**
 *
 * @author donghyuck, son
 * @since 2025-09-15
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-15  donghyuck, son: 최초 생성.
 * 2025-10-14  donghyuck, son; 사용자 롤 업데이트 기능 추가.
 *          </pre>
 */

public interface ApplicationUserService<T extends User, R extends Role> {

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-user-service";

    Page<T> findAll(Pageable pageable);

    Page<T> findByNameOrUsernameOrEmail(String keyword, Pageable pageable);

    User get(Long userId);

    Optional<T> findByUsername(String username);

    Optional<T> findByEmail(String email);

    Long findIdByUsername(String username);

    T create(T user);

    T update(Long userId, Consumer<T> mutator);

    void delete(Long userId);

    void enable(Long userId, String actor);

    void resetPassword(Long userId, String rawPassword, String actor, String reason);

    void disable(Long userId,
            String actor,
            String reason,
            OffsetDateTime until,
            boolean revokeTokens,
            boolean invalidateSessions,
            boolean notifyUser);

    // paging & search
    Page<T> search(String q, Pageable pageable);

    Page<T> getUsersByGroup(Long groupId, Pageable pageable);

    // associations
    void assignRole(Long userId, Long roleId, String by);

    void revokeRole(Long userId, Long roleId);

    BatchResult updateUserRolesBulk(Long userId, List<Long> desired, String actor);

    BatchResult assignRolesBulk(Long userId, List<Long> roles, String actor);

    void joinGroup(Long userId, Long groupId, String by);

    void leaveGroup(Long userId, Long groupId);

    // effective roles
    Set<R> findEffectiveRoles(Long userId);

    List<R> getUserRoles(Long userId);

    List<R> getUserGroupsRoles(Long userId);

}
