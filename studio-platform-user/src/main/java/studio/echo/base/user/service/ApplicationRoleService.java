package studio.echo.base.user.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.model.Role;
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


public interface ApplicationRoleService <T extends Role>{

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":application-role-service";

    Page<T> findAll(Pageable pageable);

    /**
     * 롤 단건 조회
     */
    T get(Long roleId);

    /**
     * 롤 이름으로 조회
     */
    Optional<T> findByName(String name);

    /**
     * 롤 생성
     */
    T create(T role);

    /**
     * 롤 수정 (mutator 방식)
     */
    T update(Long roleId, Consumer<T> mutator);

    /**
     * 롤 삭제
     */
    void delete(Long roleId);

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

}