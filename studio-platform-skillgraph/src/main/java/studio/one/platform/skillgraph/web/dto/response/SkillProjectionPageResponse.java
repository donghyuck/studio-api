package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;

public record SkillProjectionPageResponse(
        int offset,
        int limit,
        int returned,
        boolean hasMore,
        List<SkillProjectionSummaryDto> items) {

    public static SkillProjectionPageResponse from(
            int offset,
            int limit,
            List<SkillProjectionSummaryView> projections) {
        List<SkillProjectionSummaryDto> mapped = projections.stream()
                .limit(limit)
                .map(SkillProjectionSummaryDto::from)
                .toList();
        return new SkillProjectionPageResponse(
                offset,
                limit,
                mapped.size(),
                projections.size() > limit,
                mapped);
    }
}
