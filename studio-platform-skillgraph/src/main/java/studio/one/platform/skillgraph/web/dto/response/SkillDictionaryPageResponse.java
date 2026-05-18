package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillDictionaryView;

public record SkillDictionaryPageResponse(
        int offset,
        int limit,
        int returned,
        boolean hasMore,
        List<SkillDictionaryDto> items) {

    public static SkillDictionaryPageResponse from(int offset, int limit, List<SkillDictionaryView> skills) {
        List<SkillDictionaryDto> mapped = skills.stream()
                .limit(limit)
                .map(SkillDictionaryDto::from)
                .toList();
        return new SkillDictionaryPageResponse(
                offset,
                limit,
                mapped.size(),
                skills.size() > limit,
                mapped);
    }
}
