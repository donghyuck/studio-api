package studio.one.base.user.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.ServiceNames;
/**
 *  
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


public interface ApplicationRoleService <T extends Role, G extends Group, U extends User>{

    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX  + ":user:application-role-service";

    Page<T> getRoles(Pageable pageable);

    List<T> getRoles();

    /**
     * 롤 단건 조회
     */
    T getRoleById(Long roleId);

    /**
     * 롤 이름으로 조회
     */
    Optional<T> findRoleByName(String name);

    /**
     * 롤 생성
     */
    T createRole(T role);

    /**
     * 롤 수정 (mutator 방식)
     */
    T updateRole(Long roleId, Consumer<T> mutator);

    /**
     * 롤 삭제
     */
    void deleteRole(Long roleId);

    /**
     * 특정 사용자에게 직접 부여된 롤 조회 (페이징)
     */
    Page<T> getRolesByUser(Long userId, Pageable pageable);

    /**
     * 특정 사용자에게 직접 부여된 롤 전체 조회
     */
    List<T> getRolesByUser(Long userId);

    /**
     * 특정 그룹에 부여된 롤 조회 (페이징)
     */
    Page<T> getRolesByGroup(Long groupId, Pageable pageable);

    /**
     * 특정 그룹에 부여된 롤 전체 조회
     */
    List<T> getRolesByGroup(Long groupId);

    Page<G> findGroupsGrantedRole(Long roleId, @Nullable String q, Pageable pageable);

    /**
     * scope 값이 direct , group 로 구분되어 처리.
     *  
     * @param roleId
     * @param scope
     * @param q
     * @param pageable
     * @return
     */
    Page<U> findUsersGrantedRole(Long roleId, @Nullable String scope, @Nullable String q, Pageable pageable);
    BatchResult revokeRoleFromGroups(List<Long> groupIds, Long roleId);
    BatchResult revokeRoleFromUsers(List<Long> userIds, Long roleId);
    BatchResult assignRoleToUsers(List<Long> userIds, Long roleId, @Nullable String assignedBy, @Nullable OffsetDateTime assignedAt);
}