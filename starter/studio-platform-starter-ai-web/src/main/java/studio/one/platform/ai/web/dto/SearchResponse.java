package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SearchResponse {

    private final List<SearchResult> results;

    @JsonCreator
    public SearchResponse(@JsonProperty("results") List<SearchResult> results) {
        this.results = results;
    }

    public List<SearchResult> results() {
        return results;
    }

    public List<SearchResult> getResults() {
        return results;
    }

}