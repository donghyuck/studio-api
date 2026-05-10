package studio.one.application.wiki.infrastructure.persistence.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiPageJpaRepository extends JpaRepository<WikiPageEntity, Long>, JpaSpecificationExecutor<WikiPageEntity> {

    Optional<WikiPageEntity> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WikiPageEntity p where p.workspaceId = :workspaceId and p.slug = :slug")
    Optional<WikiPageEntity> findByWorkspaceIdAndSlugForUpdate(
            @Param("workspaceId") Long workspaceId,
            @Param("slug") String slug);

    Page<WikiPageEntity> findByWorkspaceIdAndArchivedFalse(Long workspaceId, Pageable pageable);
}
