package studio.one.platform.workspace.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, Long>, JpaSpecificationExecutor<WorkspaceEntity> {

    Optional<WorkspaceEntity> findByPath(String path);

    Optional<WorkspaceEntity> findByCompanyIdAndPath(Long companyId, String path);

    Optional<WorkspaceEntity> findByTeamIdAndPath(Long teamId, String path);

    List<WorkspaceEntity> findByTeamIdAndParentIdIsNullOrderByPositionAscWorkspaceIdAsc(Long teamId);

    boolean existsByPath(String path);

    boolean existsByCompanyIdAndPath(Long companyId, String path);

    boolean existsByTeamIdAndPath(Long teamId, String path);

    boolean existsByParentIdAndSlug(Long parentId, String slug);

    boolean existsByParentIdIsNullAndSlug(String slug);

    boolean existsByCompanyIdAndParentIdIsNullAndSlug(Long companyId, String slug);

    boolean existsByTeamIdAndParentIdIsNull(Long teamId);

    boolean existsByTeamIdAndParentIdIsNullAndSlug(Long teamId, String slug);

    long countByParentId(Long parentId);

    long countByTeamIdAndParentIdIsNull(Long teamId);

    List<WorkspaceEntity> findByParentIdOrderByPositionAscWorkspaceIdAsc(Long parentId);

    List<WorkspaceEntity> findByWorkspaceIdIn(Collection<Long> workspaceIds);
}
