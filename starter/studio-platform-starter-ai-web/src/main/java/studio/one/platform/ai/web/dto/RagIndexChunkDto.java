package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class RagIndexChunkDto {

    private final String chunkId;

    private final String documentId;

    private final String parentChunkId;

    private final Integer chunkOrder;

    private final String chunkType;

    private final String content;

    private final Double score;

    private final String headingPath;

    private final String sourceRef;

    private final Integer page;

    private final Integer slide;

    private final Map<String, Object> metadata;

    private final Instant createdAt;

    private final Instant indexedAt;

    @JsonCreator
    public RagIndexChunkDto(
            @JsonProperty("chunkId") String chunkId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("parentChunkId") String parentChunkId,
            @JsonProperty("chunkOrder") Integer chunkOrder,
            @JsonProperty("chunkType") String chunkType,
            @JsonProperty("content") String content,
            @JsonProperty("score") Double score,
            @JsonProperty("headingPath") String headingPath,
            @JsonProperty("sourceRef") String sourceRef,
            @JsonProperty("page") Integer page,
            @JsonProperty("slide") Integer slide,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("indexedAt") Instant indexedAt
    ) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.parentChunkId = parentChunkId;
        this.chunkOrder = chunkOrder;
        this.chunkType = chunkType;
        this.content = content;
        this.score = score;
        this.headingPath = headingPath;
        this.sourceRef = sourceRef;
        this.page = page;
        this.slide = slide;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.indexedAt = indexedAt;
    }

    public String chunkId() {
        return chunkId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String parentChunkId() {
        return parentChunkId;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public Integer chunkOrder() {
        return chunkOrder;
    }

    public Integer getChunkOrder() {
        return chunkOrder;
    }

    public String chunkType() {
        return chunkType;
    }

    public String getChunkType() {
        return chunkType;
    }

    public String content() {
        return content;
    }

    public String getContent() {
        return content;
    }

    public Double score() {
        return score;
    }

    public Double getScore() {
        return score;
    }

    public String headingPath() {
        return headingPath;
    }

    public String getHeadingPath() {
        return headingPath;
    }

    public String sourceRef() {
        return sourceRef;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public Integer page() {
        return page;
    }

    public Integer getPage() {
        return page;
    }

    public Integer slide() {
        return slide;
    }

    public Integer getSlide() {
        return slide;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant indexedAt() {
        return indexedAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

}