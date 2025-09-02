package studio.echo.base.user.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.echo.base.user.domain.entity.ApplicationRole;

public interface ApplicationRoleService {

    public static final String SERVICE_NAME = "components:application-role-service";

    /**
     * 롤 단건 조회
     */
    ApplicationRole get(Long roleId);

    /**
     * 롤 이름으로 조회
     */
    Optional<ApplicationRole> findByName(String name);

    /**
     * 롤 생성
     */
    ApplicationRole create(ApplicationRole role);

    /**
     * 롤 수정 (mutator 방식)
     */
    ApplicationRole update(Long roleId, Consumer<ApplicationRole> mutator);

    /**
     * 롤 삭제
     */
    void delete(Long roleId);

    /**
     * 특정 사용자에게 직접 부여된 롤 조회 (페이징)
     */
    Page<ApplicationRole> getRolesByUser(Long userId, Pageable pageable);

    /**
     * 특정 사용자에게 직접 부여된 롤 전체 조회
     */
    List<ApplicationRole> getRolesByUser(Long userId);

    /**
     * 특정 그룹에 부여된 롤 조회 (페이징)
     */
    Page<ApplicationRole> getRolesByGroup(Long groupId, Pageable pageable);

    /**
     * 특정 그룹에 부여된 롤 전체 조회
     */
    List<ApplicationRole> getRolesByGroup(Long groupId);
}