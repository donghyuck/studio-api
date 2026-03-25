package studio.one.application.web.dto;

import java.util.List;

public record SearchResponse(List<SearchResult> results) {
}
