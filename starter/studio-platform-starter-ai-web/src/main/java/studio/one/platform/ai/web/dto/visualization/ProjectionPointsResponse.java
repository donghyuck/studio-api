package studio.one.platform.ai.web.dto.visualization;

import java.util.List;

public record ProjectionPointsResponse(
        String projectionId,
        String algorithm,
        long totalCount,
        List<ProjectionPointResponse> items) {
}
