package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 리라이트/확장된 쿼리 응답 DTO.
 */
public class QueryRewriteResponseDto {

    private final String originalQuery;

    private final String expandedQuery;

    private final List<String> keywords;

    private final String prompt;

    private final String rawResponse;

    @JsonCreator
    public QueryRewriteResponseDto(
            @JsonProperty("originalQuery") String originalQuery,
            @JsonProperty("expandedQuery") String expandedQuery,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("prompt") String prompt,
            @JsonProperty("rawResponse") String rawResponse
    ) {
        this.originalQuery = originalQuery;
        this.expandedQuery = expandedQuery;
        this.keywords = keywords;
        this.prompt = prompt;
        this.rawResponse = rawResponse;
    }

    public String originalQuery() {
        return originalQuery;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String expandedQuery() {
        return expandedQuery;
    }

    public String getExpandedQuery() {
        return expandedQuery;
    }

    public List<String> keywords() {
        return keywords;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String prompt() {
        return prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public String rawResponse() {
        return rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }

}