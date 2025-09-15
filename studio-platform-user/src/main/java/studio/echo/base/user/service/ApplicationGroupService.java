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
 *      @file ApplicationGroupService.java
 *      @date 2025
 *
 */


package studio.echo.base.user.service;

import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.model.Group;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;
import studio.echo.platform.constant.ServiceNames;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-15
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-15  donghyuck, son: 최초 생성.
 * </pre>
 */


public interface ApplicationGroupService<G extends Group, R extends Role, U extends User> {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":application-group-service";

    /**
     * Get group by id
     * @param groupId
     * @return
     */
    G get(Long groupId);

    /**
     * Create new group
     * @param group
     * @return
     */
    G create(G group);

    /*
     * Update group with mutator
     * @param groupId
     * @param mutator
     * @return
     */
    G update(Long groupId, Consumer<G> mutator);

    /**
     * Delete group
     * @param groupId
     */    
    void delete(Long groupId);

    /**
     * Get groups by user
     * @param userId
     * @param pageable
     * @return
     */
    Page<G> getGroupsByUser(Long userId, Pageable pageable);
 
    /**
     * Add user to group
     * @param groupId
     * @param userId
     * @param by
     */
    void addMember(Long groupId, Long userId, String by);

    /**
     * Remove user from group
     * @param groupId
     * @param userId
     */
    void removeMember(Long groupId, Long userId);

   /**
    * Assign role to group
    * @param groupId
    * @param roleId
    * @param by
    */
    void assignRole(Long groupId, Long roleId, String by);

    /**
     * Revoke role from group
     * @param groupId
     * @param roleId
     */
    void revokeRole(Long groupId, Long roleId);

    /**
     * List group members
     * @param groupId
     * @param pageable
     * @return
     */
    Page<U> listMembers(Long groupId, Pageable pageable);

    /**
     * List group roles
     * @param groupId
     * @param pageable
     * @return
     */
    Page<R> listRoles(Long groupId, Pageable pageable);

    Page<G> findAll(Pageable pageable);

    Page<G> findAllWithMemberCount(Pageable pageable);

}
