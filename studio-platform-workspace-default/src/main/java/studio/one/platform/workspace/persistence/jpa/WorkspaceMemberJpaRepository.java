package studio.one.platform.workspace.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkspaceMemberJpaRepository
        extends JpaRepository<WorkspaceMemberEntity, Long>, JpaSpecificationExecutor<WorkspaceMemberEntity> {

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    List<WorkspaceMemberEntity> findByWorkspaceIdOrderByUserIdAsc(Long workspaceId);

    List<WorkspaceMemberEntity> findByUserIdAndWorkspaceIdIn(Long userId, Collection<Long> workspaceIds);

    List<WorkspaceMemberEntity> findByWorkspaceIdIn(Collection<Long> workspaceIds);
}
