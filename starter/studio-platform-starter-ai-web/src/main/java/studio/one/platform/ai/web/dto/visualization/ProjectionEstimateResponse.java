package studio.one.platform.ai.web.dto.visualization;

public record ProjectionEstimateResponse(
        long totalCount,
        int maxAllowed,
        boolean exceedsLimit,
        RecommendedSampling recommendedSampling) {

    public record RecommendedSampling(
            int sampleSize,
            String samplingStrategy) {
    }
}
