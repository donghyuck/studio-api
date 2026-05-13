package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class SearchResult {

    private final String documentId;

    private final String content;

    private final Map<String, Object> metadata;

    private final double score;

    @JsonCreator
    public SearchResult(
            @JsonProperty("documentId") String documentId,
            @JsonProperty("content") String content,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("score") double score
    ) {
        this.documentId = documentId;
        this.content = content;
        this.metadata = metadata;
        this.score = score;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String content() {
        return content;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public double score() {
        return score;
    }

    public double getScore() {
        return score;
    }

}