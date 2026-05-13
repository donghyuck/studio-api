package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class RagChunkPreviewItemDto {

    private final String chunkId;

    private final String content;

    private final int contentLength;

    private final Integer chunkOrder;

    private final String chunkType;

    private final String parentChunkId;

    private final String previousChunkId;

    private final String nextChunkId;

    private final String headingPath;

    private final String section;

    private final String sourceRef;

    private final Integer page;

    private final Integer slide;

    private final Map<String, Object> metadata;

    @JsonCreator
    public RagChunkPreviewItemDto(
            @JsonProperty("chunkId") String chunkId,
            @JsonProperty("content") String content,
            @JsonProperty("contentLength") int contentLength,
            @JsonProperty("chunkOrder") Integer chunkOrder,
            @JsonProperty("chunkType") String chunkType,
            @JsonProperty("parentChunkId") String parentChunkId,
            @JsonProperty("previousChunkId") String previousChunkId,
            @JsonProperty("nextChunkId") String nextChunkId,
            @JsonProperty("headingPath") String headingPath,
            @JsonProperty("section") String section,
            @JsonProperty("sourceRef") String sourceRef,
            @JsonProperty("page") Integer page,
            @JsonProperty("slide") Integer slide,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.chunkId = chunkId;
        this.content = content;
        this.contentLength = contentLength;
        this.chunkOrder = chunkOrder;
        this.chunkType = chunkType;
        this.parentChunkId = parentChunkId;
        this.previousChunkId = previousChunkId;
        this.nextChunkId = nextChunkId;
        this.headingPath = headingPath;
        this.section = section;
        this.sourceRef = sourceRef;
        this.page = page;
        this.slide = slide;
        this.metadata = metadata;
    }

    public String chunkId() {
        return chunkId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String content() {
        return content;
    }

    public String getContent() {
        return content;
    }

    public int contentLength() {
        return contentLength;
    }

    public int getContentLength() {
        return contentLength;
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

    public String parentChunkId() {
        return parentChunkId;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public String previousChunkId() {
        return previousChunkId;
    }

    public String getPreviousChunkId() {
        return previousChunkId;
    }

    public String nextChunkId() {
        return nextChunkId;
    }

    public String getNextChunkId() {
        return nextChunkId;
    }

    public String headingPath() {
        return headingPath;
    }

    public String getHeadingPath() {
        return headingPath;
    }

    public String section() {
        return section;
    }

    public String getSection() {
        return section;
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

}