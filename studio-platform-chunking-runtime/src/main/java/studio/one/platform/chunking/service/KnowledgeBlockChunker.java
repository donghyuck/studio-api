package studio.one.platform.chunking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;

/**
 * Production-facing Knowledge Block strategy backed by the Blockify/IdeaBlock
 * generation pipeline.
 */
public class KnowledgeBlockChunker extends BlockifyChunker {

    public static final String SCHEMA_VERSION = "knowledge-block-metadata-v1";
    public static final String KNOWLEDGE_BLOCK_SCHEMA_VERSION = "knowledge-block-v1";

    public KnowledgeBlockChunker(
            ChunkingProperties.BlockifyProperties properties,
            StructureBasedChunker fallbackChunker,
            BlockifyGenerator generator) {
        super(properties, fallbackChunker, generator);
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.KNOWLEDGE_BLOCK;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        return adapt(super.chunk(context));
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document, ChunkingContext context) {
        return adapt(super.chunk(document, context));
    }

    private List<Chunk> adapt(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(this::adapt)
                .toList();
    }

    private Chunk adapt(Chunk chunk) {
        Map<String, Object> attributes = new LinkedHashMap<>(chunk.metadata().attributes());
        attributes.put("schemaVersion", SCHEMA_VERSION);
        attributes.put("knowledgeBlockSchemaVersion", KNOWLEDGE_BLOCK_SCHEMA_VERSION);
        attributes.put("requestedChunkingStrategy", ChunkingStrategyType.KNOWLEDGE_BLOCK.value());
        attributes.put("knowledgeBlock", true);

        String actual = string(attributes.get("actualChunkingStrategy"));
        if (ChunkingStrategyType.BLOCKIFY.value().equals(actual)) {
            attributes.put("actualChunkingStrategy", ChunkingStrategyType.KNOWLEDGE_BLOCK.value());
        }
        copyIfPresent(attributes, "ideaBlockFingerprint", "knowledgeBlockFingerprint");
        copyIfPresent(attributes, "blockifyFingerprint", "knowledgeBlockFingerprint");
        copyIfPresent(attributes, "trustedAnswer", "knowledgeBlockSummary");
        copyIfPresent(attributes, "answer", "knowledgeBlockSummary");
        copyIfPresent(attributes, "ideaBlockCount", "knowledgeBlockCount");
        copyIfPresent(attributes, "ideaBlockFallbackCount", "knowledgeBlockFallbackCount");
        copyIfPresent(attributes, "ideaBlockFallbackReasonCounts", "knowledgeBlockFallbackReasonCounts");
        copyIfPresent(attributes, "ideaBlockSourceBlockTargetCount", "knowledgeBlockSourceBlockTargetCount");
        copyIfPresent(attributes, "ideaBlockSourceBlockCoveredCount", "knowledgeBlockSourceBlockCoveredCount");
        copyIfPresent(attributes, "ideaBlockSourceBlockCoverage", "knowledgeBlockSourceBlockCoverage");
        copyIfPresent(attributes, "ideaBlockAverageConfidence", "knowledgeBlockAverageConfidence");
        copyIfPresent(attributes, "ideaBlockDistillationEnabled", "knowledgeBlockDistillationEnabled");
        copyIfPresent(attributes, "ideaBlockDistillationStrategy", "knowledgeBlockDistillationStrategy");
        copyIfPresent(attributes, "ideaBlockDistillationDroppedDuplicateCount",
                "knowledgeBlockDistillationDroppedDuplicateCount");
        copyIfPresent(attributes, "ideaBlockDistillationGroupSize", "knowledgeBlockDistillationGroupSize");
        copyIfPresent(attributes, "ideaBlockSimilarityMergeCandidate", "knowledgeBlockSimilarityMergeCandidate");
        copyIfPresent(attributes, "ideaBlockSimilarityScore", "knowledgeBlockSimilarityScore");
        copyIfPresent(attributes, "ideaBlockSimilarityGroupKey", "knowledgeBlockSimilarityGroupKey");

        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.KNOWLEDGE_BLOCK, chunk.metadata().order())
                .sourceDocumentId(chunk.metadata().sourceDocumentId())
                .parentId(chunk.metadata().parentId())
                .chunkType(chunk.metadata().chunkType())
                .parentChunkId(chunk.metadata().parentChunkId())
                .previousChunkId(chunk.metadata().previousChunkId())
                .nextChunkId(chunk.metadata().nextChunkId())
                .section(chunk.metadata().section())
                .objectType(chunk.metadata().objectType())
                .objectId(chunk.metadata().objectId())
                .startOffset(chunk.metadata().startOffset())
                .endOffset(chunk.metadata().endOffset())
                .tokenCount(chunk.metadata().tokenCount())
                .charCount(chunk.metadata().charCount())
                .blockIds(chunk.metadata().blockIds())
                .confidence(chunk.metadata().confidence())
                .attributes(attributes)
                .build();
        return Chunk.of(chunk.id(), chunk.content(), metadata);
    }

    private void copyIfPresent(Map<String, Object> attributes, String sourceKey, String targetKey) {
        Objects.requireNonNull(attributes, "attributes");
        if (attributes.containsKey(targetKey)) {
            return;
        }
        Object value = attributes.get(sourceKey);
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            attributes.put(targetKey, value);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
