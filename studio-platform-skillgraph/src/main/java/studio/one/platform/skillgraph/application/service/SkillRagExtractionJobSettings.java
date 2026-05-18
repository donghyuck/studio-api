package studio.one.platform.skillgraph.application.service;

public record SkillRagExtractionJobSettings(
        int batchSize,
        int maxChunks,
        int maxTextBytesPerBatch) {

    public SkillRagExtractionJobSettings {
        batchSize = batchSize <= 0 ? 20 : batchSize;
        maxChunks = maxChunks <= 0 ? 5000 : maxChunks;
        maxTextBytesPerBatch = maxTextBytesPerBatch <= 0 ? 1_000_000 : maxTextBytesPerBatch;
    }
}
