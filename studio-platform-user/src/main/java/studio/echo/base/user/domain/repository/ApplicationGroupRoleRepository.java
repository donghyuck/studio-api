package studio.echo.base.user.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.domain.entity.ApplicationGroupRole;
import studio.echo.base.user.domain.entity.ApplicationGroupRoleId;
import studio.echo.base.user.domain.entity.ApplicationRole;

@Repository
public interface ApplicationGroupRoleRepository extends JpaRepository<ApplicationGroupRole, ApplicationGroupRoleId> {

    /**
     * 특정 그룹에 속한 모든 롤 조회
     */
    @Query("SELECT gr.role FROM ApplicationGroupRole gr WHERE gr.group.groupId = :groupId")
    List<ApplicationRole> findRolesByGroupId(@Param("groupId") Long groupId);

    /**
     * 특정 롤을 가진 모든 그룹 조회
     */
    @Query("SELECT gr.group FROM ApplicationGroupRole gr WHERE gr.role.roleId = :roleId")
    List<ApplicationGroup> findGroupsByRoleId(@Param("roleId") Long roleId);

    /**
     * 특정 그룹에 특정 롤이 존재하는지 여부 확인
     */
    boolean existsByGroup_GroupIdAndRole_RoleId(Long groupId, Long roleId);

    /**
     * 특정 그룹의 롤 전체 삭제
     */
    void deleteByGroup_GroupId(Long groupId);

    /**
     * 특정 롤이 연결된 그룹 관계 전체 삭제
     */
    void deleteByRole_RoleId(Long roleId);
}
