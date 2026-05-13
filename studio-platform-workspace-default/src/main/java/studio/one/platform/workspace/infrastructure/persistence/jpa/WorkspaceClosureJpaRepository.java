package studio.one.platform.workspace.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceClosureJpaRepository extends JpaRepository<WorkspaceClosureEntity, WorkspaceClosureId> {

    List<WorkspaceClosureEntity> findByIdDescendantIdOrderByDepthDesc(Long descendantId);

    List<WorkspaceClosureEntity> findByIdAncestorIdOrderByDepthAsc(Long ancestorId);

    @Query("select c.id.ancestorId "
            + "from WorkspaceClosureEntity c "
            + "where c.id.descendantId = :workspaceId "
            + "order by c.depth desc")
    List<Long> findAncestorIds(@Param("workspaceId") Long workspaceId);

    @Query("select c.id.descendantId "
            + "from WorkspaceClosureEntity c "
            + "where c.id.ancestorId = :workspaceId "
            + "order by c.depth asc")
    List<Long> findDescendantIds(@Param("workspaceId") Long workspaceId);

    @Query("select c "
            + "from WorkspaceClosureEntity c "
            + "where c.id.descendantId in :workspaceIds")
    List<WorkspaceClosureEntity> findByDescendantIds(@Param("workspaceIds") Collection<Long> workspaceIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkspaceClosureEntity c "
            + "where c.id.descendantId in :descendantIds")
    void deleteByDescendantIds(@Param("descendantIds") Collection<Long> descendantIds);
}
