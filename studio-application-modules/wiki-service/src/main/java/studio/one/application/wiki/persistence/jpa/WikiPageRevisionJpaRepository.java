package studio.one.application.wiki.persistence.jpa;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiPageRevisionJpaRepository extends JpaRepository<WikiPageRevisionEntity, Long> {

    Page<WikiPageRevisionEntity> findByPagePageIdOrderByRevisionNoDesc(Long pageId, Pageable pageable);

    Optional<WikiPageRevisionEntity> findByRevisionIdAndPagePageId(Long revisionId, Long pageId);

    @Query("select coalesce(max(r.revisionNo), 0) from WikiPageRevisionEntity r where r.page.pageId = :pageId")
    int maxRevisionNo(@Param("pageId") Long pageId);
}
