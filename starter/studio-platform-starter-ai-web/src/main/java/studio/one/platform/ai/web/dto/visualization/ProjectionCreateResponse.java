package studio.one.platform.ai.web.dto.visualization;

public record ProjectionCreateResponse(
        String projectionId,
        String status,
        String message,
        String mode,
        long totalCount,
        int projectedCount,
        boolean sampled,
        Integer sampleSize,
        String samplingStrategy,
        int maxAllowed) {

    public ProjectionCreateResponse(String projectionId, String status, String message) {
        this(projectionId, status, message, null, 0, 0, false, null, null, 0);
    }
}
