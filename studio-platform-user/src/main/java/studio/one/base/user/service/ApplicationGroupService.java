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


package studio.one.base.user.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.ServiceNames;

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

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-group-service";
    
    // ── Group: CRUD ───────────────────────────────────────────────────────────
    /**
     * Get group by id
     * @param groupId
     * @return
     */
    G getById(Long groupId);

    /**
     * Create new group
     * @param group
     * @return
     */
    G createGroup(G group);

    /*
     * Update group with mutator
     * @param groupId
     * @param mutator
     * @return
     */
    G updateGroup(Long groupId, Consumer<G> mutator);

    /**
     * Delete group
     * @param groupId
     */    
    void deleteGroup(Long groupId);

    // ── Group: Queries ───────────────────────────────────────────────────────
    /**
     * Get groups by user
     * @param userId
     * @param pageable
     * @return
     */
    Page<G> getGroupsByUser(Long userId, Pageable pageable);
 
    Page<G> getGroups(Pageable pageable);

    Page<G> getGroupsWithMemberCount(Pageable pageable);

    // ── Membership: Add / Remove ────────────────────────────────────────────
    /**
     * Add user to group
     * @param groupId
     * @param userId
     * @param by
     */
    void addMember(Long groupId, Long userId, String by);

    int addMembers(Long groupId, List<Long> userIds, String by);
    
    int addMembersBulk(Long groupId, List<Long> userIds, String by,OffsetDateTime joinedAt);

    /**
     * Remove user from group
     * @param groupId
     * @param userId
     */
    void removeMember(Long groupId, Long userId);

    int removeMembers(Long groupId, List<Long> userIds) ;

    // ── Membership: Read ─────────────────────────────────────────────────────

    /**
     * List group members
     * @param groupId
     * @param pageable
     * @return
     */
    Page<U> getMembers(Long groupId, Pageable pageable);


    // ── Roles: Assign / Revoke / Read ────────────────────────────────────────

    BatchResult updateGroupRolesBulk(Long groupId, List<Long> roles, String actor);

    BatchResult assignRolesBulk(Long groupId, List<Long> roles, String actor) ;

    BatchResult assignRoles(Long groupId, List<Long> roles, String actor) ;


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
     * List group roles
     * @param groupId
     * @param pageable
     * @return
     */
    Page<R> getRoles(Long groupId, Pageable pageable);
    
    List<R> getRoles(Long groupId);
}
