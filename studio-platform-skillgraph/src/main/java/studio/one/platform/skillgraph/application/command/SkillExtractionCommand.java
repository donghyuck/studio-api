package studio.one.platform.skillgraph.application.command;

public record SkillExtractionCommand(
        String sourceType,
        String sourceId,
        String chunkId,
        String text,
        String sourceMarkdownDocumentId,
        String sourceMarkdownRevisionId,
        String sourceMetadataJson) {

    public SkillExtractionCommand(String sourceType, String sourceId, String chunkId, String text) {
        this(sourceType, sourceId, chunkId, text, null, null, null);
    }
}
