package studio.echo.base.user.service;

import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.entity.ApplicationUser;

public interface ApplicationGroupService {

    public static final String SERVICE_NAME = "components:application-group-service";

    /**
     * Get group by id
     * @param groupId
     * @return
     */
    ApplicationGroup get(Long groupId);

    /**
     * Create new group
     * @param group
     * @return
     */
    ApplicationGroup create(ApplicationGroup group);

    /*
     * Update group with mutator
     * @param groupId
     * @param mutator
     * @return
     */
    ApplicationGroup update(Long groupId, Consumer<ApplicationGroup> mutator);

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
    Page<ApplicationGroup> getGroupsByUser(Long userId, Pageable pageable);
 
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
    Page<ApplicationUser> listMembers(Long groupId, Pageable pageable);

    /**
     * List group roles
     * @param groupId
     * @param pageable
     * @return
     */
    Page<ApplicationRole> listRoles(Long groupId, Pageable pageable);


    Page<ApplicationGroup> findAll(Pageable pageable);

}
