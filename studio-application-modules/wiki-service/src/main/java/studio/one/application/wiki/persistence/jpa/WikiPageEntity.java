package studio.one.application.wiki.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.application.wiki.model.WikiPageSummary;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "TB_APPLICATION_WORKSPACE_WIKI_PAGE",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_WORKSPACE_WIKI_PAGE_WORKSPACE_SLUG",
                columnNames = { "WORKSPACE_ID", "SLUG" }),
        indexes = {
                @Index(name = "IDX_WORKSPACE_WIKI_PAGE_WORKSPACE", columnList = "WORKSPACE_ID"),
                @Index(name = "IDX_WORKSPACE_WIKI_PAGE_CURRENT_REV", columnList = "CURRENT_REVISION_ID"),
                @Index(name = "IDX_WORKSPACE_WIKI_PAGE_ARCHIVED", columnList = "ARCHIVED")
        })
@Getter
@Setter
@NoArgsConstructor
public class WikiPageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAGE_ID", nullable = false)
    private Long pageId;

    @Column(name = "WORKSPACE_ID", nullable = false)
    private Long workspaceId;

    @Column(name = "SLUG", nullable = false, length = 200)
    private String slug;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "CURRENT_REVISION_ID")
    private Long currentRevisionId;

    @Column(name = "CURRENT_REVISION_NO", nullable = false)
    private int currentRevisionNo;

    @Column(name = "ARCHIVED", nullable = false)
    private boolean archived;

    @Column(name = "ARCHIVED_AT")
    private Instant archivedAt;

    @Column(name = "ARCHIVED_BY")
    private Long archivedBy;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "UPDATED_BY", nullable = false)
    private Long updatedBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    public WikiPageSummary toSummary() {
        return new WikiPageSummary(
                pageId,
                workspaceId,
                slug,
                title,
                currentRevisionId,
                currentRevisionNo,
                archived,
                createdAt,
                updatedAt);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
