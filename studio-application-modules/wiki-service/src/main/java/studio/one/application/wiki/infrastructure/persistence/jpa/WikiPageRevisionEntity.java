package studio.one.application.wiki.infrastructure.persistence.jpa;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.application.wiki.domain.model.WikiRevisionSummary;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "TB_APPLICATION_WORKSPACE_WIKI_PAGE_REVISION",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_WORKSPACE_WIKI_REVISION_PAGE_NO",
                columnNames = { "PAGE_ID", "REVISION_NO" }),
        indexes = {
                @Index(name = "IDX_WORKSPACE_WIKI_REVISION_PAGE", columnList = "PAGE_ID"),
                @Index(name = "IDX_WORKSPACE_WIKI_REVISION_WORKSPACE", columnList = "WORKSPACE_ID"),
                @Index(name = "IDX_WORKSPACE_WIKI_REVISION_CREATED_AT", columnList = "CREATED_AT")
        })
@Getter
@Setter
@NoArgsConstructor
public class WikiPageRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVISION_ID", nullable = false)
    private Long revisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PAGE_ID", nullable = false)
    private WikiPageEntity page;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "REVISION_NO", nullable = false)
    private int revisionNo;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "MARKDOWN", nullable = false, columnDefinition = "TEXT")
    private String markdown;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    public WikiRevisionSummary toSummary(String slug) {
        return new WikiRevisionSummary(
                revisionId,
                page.getPageId(),
                workspaceId,
                slug,
                title,
                revisionNo,
                createdBy,
                createdAt);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
