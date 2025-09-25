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

package studio.echo.base.user.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;
import studio.echo.platform.constant.ServiceNames;

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
 *          </pre>
 */

public interface ApplicationUserService<T extends User> {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":application-user-service";

    Page<T> findAll(Pageable pageable);

    User get(Long userId);

    Optional<T> findByUsername(String username);

    T create(T user);

    T update(Long userId, Consumer<T> mutator);

    void delete(Long userId);

    void enable(Long userId, String actor);

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

    void joinGroup(Long userId, Long groupId, String by);

    void leaveGroup(Long userId, Long groupId);

    // effective roles
    Set<Role> findEffectiveRoles(Long userId);
}
