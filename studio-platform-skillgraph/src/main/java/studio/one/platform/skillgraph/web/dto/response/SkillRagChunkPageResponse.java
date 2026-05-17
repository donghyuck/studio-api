package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

public record SkillRagChunkPageResponse(
        List<SkillRagChunkPreviewDto> items,
        int offset,
        int limit,
        int returned,
        Integer total,
        boolean hasMore) {
}
