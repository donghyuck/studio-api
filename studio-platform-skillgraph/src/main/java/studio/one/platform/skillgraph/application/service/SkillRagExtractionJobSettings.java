package studio.one.platform.skillgraph.application.service;

import java.time.Duration;

public record SkillRagExtractionJobSettings(
        int batchSize,
        int maxChunks,
        int maxTextBytesPerBatch,
        Duration leaseDuration,
        int maxAutoRetries) {

    public SkillRagExtractionJobSettings(int batchSize, int maxChunks, int maxTextBytesPerBatch) {
        this(batchSize, maxChunks, maxTextBytesPerBatch, Duration.ofMinutes(2), 3);
    }

    public SkillRagExtractionJobSettings {
        batchSize = batchSize <= 0 ? 20 : batchSize;
        maxChunks = maxChunks <= 0 ? 5000 : maxChunks;
        maxTextBytesPerBatch = maxTextBytesPerBatch <= 0 ? 1_000_000 : maxTextBytesPerBatch;
        leaseDuration = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofMinutes(2)
                : leaseDuration;
        maxAutoRetries = maxAutoRetries <= 0 ? 3 : maxAutoRetries;
    }
}
