package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

import javax.validation.constraints.NotBlank;

public class RagChunkPreviewRequestDto {

    @NotBlank
    private final String text;

    private final String documentId;

    private final String objectType;

    private final String objectId;

    private final String contentType;

    private final String filename;

    private final String strategy;

    private final Integer maxSize;

    private final Integer overlap;

    private final String unit;

    private final Map<String, Object> metadata;

    @JsonCreator
    public RagChunkPreviewRequestDto(
            @JsonProperty("text") String text,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("filename") String filename,
            @JsonProperty("strategy") String strategy,
            @JsonProperty("maxSize") Integer maxSize,
            @JsonProperty("overlap") Integer overlap,
            @JsonProperty("unit") String unit,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.text = text;
        this.documentId = documentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.contentType = contentType;
        this.filename = filename;
        this.strategy = strategy;
        this.maxSize = maxSize;
        this.overlap = overlap;
        this.unit = unit;
        this.metadata = metadata;
    }

    public String text() {
        return text;
    }

    public String getText() {
        return text;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String objectType() {
        return objectType;
    }

    public String getObjectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    public String contentType() {
        return contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public String filename() {
        return filename;
    }

    public String getFilename() {
        return filename;
    }

    public String strategy() {
        return strategy;
    }

    public String getStrategy() {
        return strategy;
    }

    public Integer maxSize() {
        return maxSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public Integer overlap() {
        return overlap;
    }

    public Integer getOverlap() {
        return overlap;
    }

    public String unit() {
        return unit;
    }

    public String getUnit() {
        return unit;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

}