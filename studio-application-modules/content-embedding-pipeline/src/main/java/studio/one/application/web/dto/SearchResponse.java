package studio.one.application.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SearchResponse {
    private List<SearchResult> results;

    public SearchResponse() {
    }

    public SearchResponse(List<SearchResult> results) {
        this.results = results;
    }

    public List<SearchResult> results() {
        return results;
    }
}
