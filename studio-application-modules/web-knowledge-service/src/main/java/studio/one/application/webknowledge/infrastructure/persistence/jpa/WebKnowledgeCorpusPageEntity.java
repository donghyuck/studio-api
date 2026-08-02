package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "web_knowledge_corpus_page")
public class WebKnowledgeCorpusPageEntity {

    @Id
    @Column(name = "corpus_page_id", length = 80, nullable = false)
    private String corpusPageId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "corpus_revision_id", length = 80, nullable = false)
    private String corpusRevisionId;
    @Column(name = "page_id", length = 80, nullable = false)
    private String pageId;
    @Column(name = "page_revision_id", length = 80, nullable = false)
    private String pageRevisionId;
    @Column(name = "page_order", nullable = false)
    private int pageOrder;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebKnowledgeCorpusPageEntity() {
    }

    public WebKnowledgeCorpusPageEntity(
            String corpusPageId,
            Long workspaceId,
            String sourceId,
            String corpusRevisionId,
            String pageId,
            String pageRevisionId,
            int pageOrder,
            Instant now) {
        this.corpusPageId = corpusPageId;
        this.workspaceId = workspaceId;
        this.sourceId = sourceId;
        this.corpusRevisionId = corpusRevisionId;
        this.pageId = pageId;
        this.pageRevisionId = pageRevisionId;
        this.pageOrder = pageOrder;
        this.createdAt = now;
    }

    public String corpusPageId() { return corpusPageId; }
    public Long workspaceId() { return workspaceId; }
    public String sourceId() { return sourceId; }
    public String corpusRevisionId() { return corpusRevisionId; }
    public String pageId() { return pageId; }
    public String pageRevisionId() { return pageRevisionId; }
    public int pageOrder() { return pageOrder; }
    public Instant createdAt() { return createdAt; }
}
