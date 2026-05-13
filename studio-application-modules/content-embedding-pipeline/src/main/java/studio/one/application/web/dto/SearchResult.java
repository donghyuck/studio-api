package studio.one.application.web.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SearchResult {
    private String documentId;
    private String content;
    private Map<String, Object> metadata;
    private double score;

    public SearchResult() {
    }

    public SearchResult(String documentId, String content, Map<String, Object> metadata, double score) {
        this.documentId = documentId;
        this.content = content;
        this.metadata = metadata;
        this.score = score;
    }

    public String documentId() {
        return documentId;
    }

    public String content() {
        return content;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public double score() {
        return score;
    }
}
