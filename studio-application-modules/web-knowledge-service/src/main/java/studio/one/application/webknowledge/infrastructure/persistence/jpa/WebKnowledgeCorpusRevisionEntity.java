package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "web_knowledge_corpus_revision")
public class WebKnowledgeCorpusRevisionEntity {

    @Id
    @Column(name = "corpus_revision_id", length = 80, nullable = false)
    private String corpusRevisionId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "run_id", length = 80, nullable = false)
    private String runId;
    @Column(name = "status", length = 32, nullable = false)
    private String status;
    @Column(name = "manifest_hash", length = 64, nullable = false)
    private String manifestHash;
    @Column(name = "policy_hash", length = 64, nullable = false)
    private String policyHash;
    @Column(name = "embedding_space_id", length = 200, nullable = false)
    private String embeddingSpaceId;
    @Column(name = "page_count", nullable = false)
    private int pageCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgeCorpusRevisionEntity() {
    }

    public WebKnowledgeCorpusRevisionEntity(
            String corpusRevisionId,
            Long workspaceId,
            String sourceId,
            String runId,
            String manifestHash,
            String policyHash,
            String embeddingSpaceId,
            int pageCount,
            Instant now) {
        this.corpusRevisionId = corpusRevisionId;
        this.workspaceId = workspaceId;
        this.sourceId = sourceId;
        this.runId = runId;
        this.status = "STAGED";
        this.manifestHash = manifestHash;
        this.policyHash = policyHash;
        this.embeddingSpaceId = embeddingSpaceId;
        this.pageCount = pageCount;
        this.createdAt = now;
    }

    public String corpusRevisionId() { return corpusRevisionId; }
    public Long workspaceId() { return workspaceId; }
    public String sourceId() { return sourceId; }
    public String runId() { return runId; }
    public String status() { return status; }
    public String manifestHash() { return manifestHash; }
    public String policyHash() { return policyHash; }
    public String embeddingSpaceId() { return embeddingSpaceId; }
    public int pageCount() { return pageCount; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }

    public void complete(Instant now) {
        status = "COMPLETED";
        completedAt = now;
    }
}
