package studio.one.platform.skillgraph.application.command;

public record SkillExtractionCommand(
        String sourceType,
        String sourceId,
        String chunkId,
        String text) {
}
