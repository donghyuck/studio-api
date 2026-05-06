package studio.one.application.wiki.persistence.jpa;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WikiPageJpaRepository extends JpaRepository<WikiPageEntity, Long>, JpaSpecificationExecutor<WikiPageEntity> {

    Optional<WikiPageEntity> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    Page<WikiPageEntity> findByWorkspaceIdAndArchivedFalse(Long workspaceId, Pageable pageable);
}
