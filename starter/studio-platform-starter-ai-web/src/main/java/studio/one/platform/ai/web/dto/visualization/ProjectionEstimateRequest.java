package studio.one.platform.ai.web.dto.visualization;

import java.util.List;
import java.util.Map;

public record ProjectionEstimateRequest(
        List<String> targetTypes,
        Map<String, Object> filters,
        String mode,
        Integer sampleSize,
        String samplingStrategy) {
}
