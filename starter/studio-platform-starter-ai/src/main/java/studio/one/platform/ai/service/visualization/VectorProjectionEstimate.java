package studio.one.platform.ai.service.visualization;

import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;

public record VectorProjectionEstimate(
        long totalCount,
        int maxAllowed,
        boolean exceedsLimit,
        int recommendedSampleSize,
        ProjectionSamplingStrategy recommendedSamplingStrategy) {
}
