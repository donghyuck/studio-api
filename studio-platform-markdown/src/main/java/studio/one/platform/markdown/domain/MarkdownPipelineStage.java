package studio.one.platform.markdown.domain;

public enum MarkdownPipelineStage {
    METADATA_ENRICHMENT,
    CHUNKING,
    RAG_INDEX,
    SKILL_EXTRACTION,
    COMPLETED
}
