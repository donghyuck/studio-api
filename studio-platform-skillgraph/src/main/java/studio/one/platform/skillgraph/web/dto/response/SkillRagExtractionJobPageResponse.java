package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;

public record SkillRagExtractionJobPageResponse(
        int offset,
        int limit,
        int returned,
        boolean hasMore,
        List<SkillRagExtractionJobResponse> items) {

    public static SkillRagExtractionJobPageResponse from(int offset, int limit, List<SkillRagExtractionJob> jobs) {
        List<SkillRagExtractionJobResponse> mapped = jobs.stream()
                .limit(limit)
                .map(SkillRagExtractionJobResponse::from)
                .toList();
        return new SkillRagExtractionJobPageResponse(
                offset,
                limit,
                mapped.size(),
                jobs.size() > limit,
                mapped);
    }
}
