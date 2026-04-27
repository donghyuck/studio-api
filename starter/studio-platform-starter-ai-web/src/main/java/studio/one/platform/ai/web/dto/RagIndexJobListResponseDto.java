package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagIndexJobListResponseDto(
        List<RagIndexJobDto> items,
        long total,
        int offset,
        int limit) {
}
