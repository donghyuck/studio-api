package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;

public record SkillRagExtractionJobItemPageResponse(
        String jobId,
        int offset,
        int limit,
        int returned,
        boolean hasMore,
        List<SkillRagExtractionJobItemResponse> items) {

    public static SkillRagExtractionJobItemPageResponse from(
            String jobId,
            int offset,
            int limit,
            List<SkillRagExtractionJobItem> items) {
        List<SkillRagExtractionJobItemResponse> mapped = items.stream()
                .limit(limit)
                .map(SkillRagExtractionJobItemResponse::from)
                .toList();
        return new SkillRagExtractionJobItemPageResponse(
                jobId,
                offset,
                limit,
                mapped.size(),
                items.size() > limit,
                mapped);
    }
}
