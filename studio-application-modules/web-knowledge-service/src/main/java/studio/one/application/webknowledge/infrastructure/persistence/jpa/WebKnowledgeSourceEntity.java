package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "web_knowledge_source")
public class WebKnowledgeSourceEntity {

    @Id
    @Column(name = "source_id", length = 80, nullable = false)
    private String sourceId;
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    @Column(name = "input_url", length = 2048, nullable = false)
    private String inputUrl;
    @Column(name = "normalized_url", length = 2048, nullable = false)
    private String normalizedUrl;
    @Column(name = "normalized_url_hash", length = 64, nullable = false)
    private String normalizedUrlHash;
    @Column(name = "active_dedupe_key", length = 256)
    private String activeDedupeKey;
    @Column(name = "canonical_url", length = 2048)
    private String canonicalUrl;
    @Column(name = "source_host", length = 255, nullable = false)
    private String host;
    @Column(name = "display_name", length = 300)
    private String displayName;
    @Column(name = "embedding_deployment_id", length = 160, nullable = false)
    private String embeddingDeploymentId;
    @Column(name = "embedding_space_id", length = 200)
    private String embeddingSpaceId;
    @Column(name = "collection_mode", length = 32, nullable = false)
    private String collectionMode;
    @Column(name = "crawl_policy_json", length = 8000)
    private String crawlPolicyJson;
    @Column(name = "crawl_policy_hash", length = 64)
    private String crawlPolicyHash;
    @Column(name = "current_revision_id", length = 80)
    private String currentRevisionId;
    @Column(name = "current_corpus_revision_id", length = 80)
    private String currentCorpusRevisionId;
    @Column(name = "status", length = 32, nullable = false)
    private String status;
    @Column(name = "archived", nullable = false)
    private boolean archived;
    @Column(name = "created_by", length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected WebKnowledgeSourceEntity() {
    }

    public WebKnowledgeSourceEntity(String sourceId, Long workspaceId, String inputUrl, String normalizedUrl,
            String normalizedUrlHash, String host, String displayName, String embeddingDeploymentId,
            String embeddingSpaceId, String createdBy, Instant now) {
        this(sourceId, workspaceId, inputUrl, normalizedUrl, normalizedUrlHash, host, displayName,
                embeddingDeploymentId, embeddingSpaceId, "SINGLE_PAGE", null, null, createdBy, now);
    }

    public WebKnowledgeSourceEntity(String sourceId, Long workspaceId, String inputUrl, String normalizedUrl,
            String normalizedUrlHash, String host, String displayName, String embeddingDeploymentId,
            String embeddingSpaceId, String collectionMode, String crawlPolicyJson, String crawlPolicyHash,
            String createdBy, Instant now) {
        this.sourceId = sourceId;
        this.workspaceId = workspaceId;
        this.inputUrl = inputUrl;
        this.normalizedUrl = normalizedUrl;
        this.normalizedUrlHash = normalizedUrlHash;
        this.activeDedupeKey = normalizedUrlHash + ":" + embeddingDeploymentId;
        this.host = host;
        this.displayName = displayName;
        this.embeddingDeploymentId = embeddingDeploymentId;
        this.embeddingSpaceId = embeddingSpaceId;
        this.collectionMode = collectionMode == null ? "SINGLE_PAGE" : collectionMode;
        this.crawlPolicyJson = crawlPolicyJson;
        this.crawlPolicyHash = crawlPolicyHash;
        this.status = "PENDING";
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String sourceId() { return sourceId; }
    public Long workspaceId() { return workspaceId; }
    public String inputUrl() { return inputUrl; }
    public String normalizedUrl() { return normalizedUrl; }
    public String normalizedUrlHash() { return normalizedUrlHash; }
    public String canonicalUrl() { return canonicalUrl; }
    public String host() { return host; }
    public String displayName() { return displayName; }
    public String embeddingDeploymentId() { return embeddingDeploymentId; }
    public String embeddingSpaceId() { return embeddingSpaceId; }
    public String collectionMode() { return collectionMode; }
    public String crawlPolicyJson() { return crawlPolicyJson; }
    public String crawlPolicyHash() { return crawlPolicyHash; }
    public String currentRevisionId() { return currentRevisionId; }
    public String currentCorpusRevisionId() { return currentCorpusRevisionId; }
    public String status() { return status; }
    public boolean archived() { return archived; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void status(String value, Instant now) {
        this.status = value;
        this.updatedAt = now;
    }

    public void complete(String revisionId, String canonicalUrl, String embeddingSpaceId, Instant now) {
        this.currentRevisionId = revisionId;
        this.canonicalUrl = canonicalUrl;
        this.embeddingSpaceId = embeddingSpaceId;
        this.status = "COMPLETED";
        this.updatedAt = now;
    }

    public void completeCorpus(
            String corpusRevisionId,
            String canonicalUrl,
            String embeddingSpaceId,
            Instant now) {
        completeCorpus(corpusRevisionId, canonicalUrl, embeddingSpaceId, null, now);
    }

    public void completeCorpus(
            String corpusRevisionId,
            String canonicalUrl,
            String embeddingSpaceId,
            String suggestedDisplayName,
            Instant now) {
        this.currentCorpusRevisionId = corpusRevisionId;
        this.canonicalUrl = canonicalUrl;
        this.embeddingSpaceId = embeddingSpaceId;
        if ((this.displayName == null || this.displayName.isBlank())
                && suggestedDisplayName != null
                && !suggestedDisplayName.isBlank()) {
            this.displayName = suggestedDisplayName;
        }
        this.status = "COMPLETED";
        this.updatedAt = now;
    }

    public void crawlPolicy(String value, String hash, Instant now) {
        this.crawlPolicyJson = value;
        this.crawlPolicyHash = hash;
        this.updatedAt = now;
    }

    public void unchanged(Instant now) {
        this.status = "UNCHANGED";
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.archived = true;
        this.activeDedupeKey = null;
        this.status = "CANCELLED";
        this.updatedAt = now;
    }
}
