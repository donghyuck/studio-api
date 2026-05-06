package studio.one.platform.workspace.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, Long>, JpaSpecificationExecutor<WorkspaceEntity> {

    Optional<WorkspaceEntity> findByPath(String path);

    Optional<WorkspaceEntity> findByCompanyIdAndPath(Long companyId, String path);

    boolean existsByPath(String path);

    boolean existsByCompanyIdAndPath(Long companyId, String path);

    boolean existsByParentIdAndSlug(Long parentId, String slug);

    boolean existsByParentIdIsNullAndSlug(String slug);

    long countByParentId(Long parentId);

    List<WorkspaceEntity> findByParentIdOrderByPositionAscWorkspaceIdAsc(Long parentId);

    List<WorkspaceEntity> findByWorkspaceIdIn(Collection<Long> workspaceIds);
}
