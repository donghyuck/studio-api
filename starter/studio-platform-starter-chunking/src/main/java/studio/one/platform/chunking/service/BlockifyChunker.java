package studio.one.platform.chunking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.core.NormalizedDocumentChunker;

public class BlockifyChunker implements NormalizedDocumentChunker {

    static final String SCHEMA_VERSION = "blockify-metadata-v1";
    static final String VALIDATION_RULE_VALIDATED = "RULE_VALIDATED";
    static final String VALIDATION_FALLBACK = "FALLBACK";
    static final String VALIDATION_REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private final ChunkingProperties.BlockifyProperties properties;
    private final StructureBasedChunker fallbackChunker;
    private final BlockifyGenerator generator;

    public BlockifyChunker(
            ChunkingProperties.BlockifyProperties properties,
            StructureBasedChunker fallbackChunker,
            BlockifyGenerator generator) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.fallbackChunker = Objects.requireNonNull(fallbackChunker, "fallbackChunker");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.BLOCKIFY;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        NormalizedDocument document = NormalizedDocument.builder(context.sourceDocumentId())
                .plainText(context.text())
                .sourceFormat(context.contentType())
                .filename(context.filename())
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, context.text())
                        .id(sectionId(context.sourceDocumentId(), 0))
                        .order(0)
                        .metadata(context.metadata())
                        .build()))
                .metadata(context.metadata())
                .build();
        return chunk(document, context);
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document, ChunkingContext context) {
        if (document == null || document.chunkableText().isBlank()) {
            return List.of();
        }
        List<Section> sections = splitSections(document);
        List<Chunk> chunks = new ArrayList<>();
        int order = 0;
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            if (index >= properties.getMaxSectionsPerDocument()) {
                order = appendFallback(document, context, section, chunks, order, "MAX_SECTIONS_EXCEEDED");
                continue;
            }
            if (containsTable(section.blocks())) {
                order = appendFallback(document, context, section, chunks, order, "TABLE_SECTION");
                continue;
            }
            if (ChunkSizing.estimateTokens(content(section.blocks())) > properties.getMaxInputTokens()) {
                order = appendFallback(document, context, section, chunks, order, "MAX_INPUT_TOKENS_EXCEEDED");
                continue;
            }
            try {
                List<BlockifyBlock> blocks = generator.generate(new BlockifyGenerationRequest(
                        effectiveSourceDocumentId(document, context),
                        section.sectionId(),
                        section.headingPath(),
                        section.blocks(),
                        properties.getPromptVersion(),
                        properties.getGeneratorModel(),
                        properties.getTemperature(),
                        properties.getTopP(),
                        properties.getMaxBlocksPerSection()));
                int before = chunks.size();
                for (BlockifyBlock block : blocks.stream().limit(properties.getMaxBlocksPerSection()).toList()) {
                    if (!isValid(block, section)) {
                        continue;
                    }
                    chunks.add(toBlockifyChunk(document, context, section, block, order++));
                }
                if (chunks.size() == before) {
                    order = appendFallback(document, context, section, chunks, order, "NO_VALID_BLOCKIFY_BLOCK");
                }
            } catch (RuntimeException ex) {
                order = appendFallback(document, context, section, chunks, order,
                        ex.getClass().getSimpleName().toUpperCase(Locale.ROOT));
            }
        }
        return linkNeighbors(chunks);
    }

    private boolean isValid(BlockifyBlock block, Section section) {
        if (block == null || isBlank(block.question()) || isBlank(block.answer())) {
            return false;
        }
        if (!properties.isRequireSourceEvidence()) {
            return true;
        }
        if (block.sourceEvidence() == null || block.sourceEvidence().isEmpty()) {
            return false;
        }
        String sourceText = content(section.blocks()).replaceAll("\\s+", " ");
        return block.sourceEvidence().stream()
                .map(BlockifySourceEvidence::text)
                .filter(text -> text != null && !text.isBlank())
                .map(text -> text.replaceAll("\\s+", " "))
                .anyMatch(sourceText::contains);
    }

    private Chunk toBlockifyChunk(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            BlockifyBlock block,
            int order) {
        String content = blockContent(block);
        Map<String, Object> attributes = metadata(document, section, block, ChunkingStrategyType.BLOCKIFY,
                VALIDATION_RULE_VALIDATED, null);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .chunkType(ChunkType.CHILD)
                .section(section.headingPath())
                .objectType(context.objectType())
                .objectId(context.objectId())
                .charCount(content.length())
                .tokenCount(ChunkSizing.estimateTokens(content))
                .blockIds(blockIds(section.blocks()))
                .confidence(block.confidence())
                .attributes(attributes)
                .build();
        return Chunk.of(chunkId(effectiveSourceDocumentId(document, context), order), content, metadata);
    }

    private int appendFallback(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            List<Chunk> chunks,
            int order,
            String reason) {
        NormalizedDocument sectionDocument = NormalizedDocument.builder(effectiveSourceDocumentId(document, context))
                .plainText(content(section.blocks()))
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(section.blocks())
                .metadata(document.metadata())
                .build();
        ChunkingContext fallbackContext = ChunkingContext.builder(sectionDocument.chunkableText())
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .contentType(context.contentType())
                .filename(context.filename())
                .objectType(context.objectType())
                .objectId(context.objectId())
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(context.maxSize())
                .overlap(context.overlap())
                .unit(context.unit() == null ? ChunkUnit.CHARACTER : context.unit())
                .metadata(context.metadata())
                .build();
        for (Chunk fallback : fallbackChunker.chunk(sectionDocument, fallbackContext)) {
            Map<String, Object> attributes = new LinkedHashMap<>(fallback.metadata().attributes());
            attributes.putAll(metadata(document, section, null, ChunkingStrategyType.STRUCTURE_BASED,
                    VALIDATION_FALLBACK, reason));
            ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, order)
                    .sourceDocumentId(fallback.metadata().sourceDocumentId())
                    .chunkType(fallback.metadata().chunkType())
                    .parentChunkId(fallback.metadata().parentChunkId())
                    .section(fallback.metadata().section())
                    .objectType(fallback.metadata().objectType())
                    .objectId(fallback.metadata().objectId())
                    .startOffset(fallback.metadata().startOffset())
                    .endOffset(fallback.metadata().endOffset())
                    .tokenCount(fallback.metadata().tokenCount())
                    .charCount(fallback.metadata().charCount())
                    .blockIds(fallback.metadata().blockIds())
                    .confidence(fallback.metadata().confidence())
                    .attributes(attributes)
                    .build();
            chunks.add(Chunk.of(chunkId(effectiveSourceDocumentId(document, context), order++),
                    fallback.content(), metadata));
        }
        return order;
    }

    private Map<String, Object> metadata(
            NormalizedDocument document,
            Section section,
            BlockifyBlock block,
            ChunkingStrategyType actualStrategy,
            String validationStatus,
            String fallbackReason) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put("schemaVersion", SCHEMA_VERSION);
        metadata.put("requestedChunkingStrategy", ChunkingStrategyType.BLOCKIFY.value());
        metadata.put("actualChunkingStrategy", actualStrategy.value());
        metadata.put("validationStatus", validationStatus);
        metadata.put("promptVersion", properties.getPromptVersion());
        metadata.put("generatorModel", properties.getGeneratorModel());
        metadata.put("temperature", properties.getTemperature());
        metadata.put("topP", properties.getTopP());
        put(metadata, "sourceSectionId", section.sectionId());
        put(metadata, ChunkMetadata.KEY_HEADING_PATH, section.headingPath());
        if (fallbackReason != null) {
            metadata.put("fallbackReason", fallbackReason);
        }
        if (block != null) {
            put(metadata, "title", block.title());
            put(metadata, "question", block.question());
            put(metadata, "answer", block.answer());
            put(metadata, "keywords", block.keywords());
            put(metadata, "tags", block.tags());
            put(metadata, "sourceEvidence", block.sourceEvidence());
            metadata.put("blockifyFingerprint", fingerprint(document, section, block));
        }
        return metadata;
    }

    private String fingerprint(NormalizedDocument document, Section section, BlockifyBlock block) {
        String raw = String.join("\n",
                document.sourceDocumentId(),
                section.sectionId(),
                String.valueOf(section.startOrder()),
                String.valueOf(section.endOrder()),
                normalize(block.question()),
                normalize(block.answer()),
                properties.getGeneratorModel(),
                properties.getPromptVersion());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private List<Section> splitSections(NormalizedDocument document) {
        List<NormalizedBlock> blocks = document.blocks().isEmpty()
                ? List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, document.chunkableText())
                        .id(sectionId(document.sourceDocumentId(), 0))
                        .order(0)
                        .build())
                : document.blocks();
        List<Section> sections = new ArrayList<>();
        List<NormalizedBlock> current = new ArrayList<>();
        String headingPath = "";
        int startOrder = 0;
        for (NormalizedBlock block : blocks) {
            if (isHeading(block)) {
                if (!current.isEmpty()) {
                    sections.add(section(document, sections.size(), headingPath, startOrder, current));
                }
                current = new ArrayList<>();
                headingPath = resolveHeadingPath(block);
                startOrder = block.order() == null ? sections.size() : block.order();
                current.add(block);
                continue;
            }
            if (current.isEmpty()) {
                startOrder = block.order() == null ? sections.size() : block.order();
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            sections.add(section(document, sections.size(), headingPath, startOrder, current));
        }
        return sections;
    }

    private Section section(
            NormalizedDocument document,
            int index,
            String headingPath,
            int startOrder,
            List<NormalizedBlock> blocks) {
        int endOrder = blocks.stream()
                .map(NormalizedBlock::order)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(startOrder);
        return new Section(sectionId(document.sourceDocumentId(), index), headingPath, startOrder, endOrder, List.copyOf(blocks));
    }

    private List<Chunk> linkNeighbors(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<Chunk> linked = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Chunk previous = index == 0 ? null : chunks.get(index - 1);
            Chunk next = index == chunks.size() - 1 ? null : chunks.get(index + 1);
            ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                    .sourceDocumentId(chunk.metadata().sourceDocumentId())
                    .parentId(chunk.metadata().parentId())
                    .chunkType(chunk.metadata().chunkType())
                    .parentChunkId(chunk.metadata().parentChunkId())
                    .previousChunkId(previous == null ? null : previous.id())
                    .nextChunkId(next == null ? null : next.id())
                    .section(chunk.metadata().section())
                    .objectType(chunk.metadata().objectType())
                    .objectId(chunk.metadata().objectId())
                    .startOffset(chunk.metadata().startOffset())
                    .endOffset(chunk.metadata().endOffset())
                    .tokenCount(chunk.metadata().tokenCount())
                    .charCount(chunk.metadata().charCount())
                    .blockIds(chunk.metadata().blockIds())
                    .confidence(chunk.metadata().confidence())
                    .attributes(chunk.metadata().attributes())
                    .build();
            linked.add(Chunk.of(chunk.id(), chunk.content(), metadata));
        }
        return linked;
    }

    private boolean containsTable(List<NormalizedBlock> blocks) {
        return blocks.stream().anyMatch(block -> block.type() == NormalizedBlockType.TABLE);
    }

    private boolean isHeading(NormalizedBlock block) {
        return block.type() == NormalizedBlockType.TITLE || block.type() == NormalizedBlockType.HEADING;
    }

    private String resolveHeadingPath(NormalizedBlock block) {
        if (block.headingPath() != null && !block.headingPath().isBlank()) {
            return block.headingPath();
        }
        return block.text();
    }

    private String content(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String blockContent(BlockifyBlock block) {
        StringBuilder builder = new StringBuilder();
        builder.append("제목: ").append(block.title()).append("\n\n");
        builder.append("핵심 질문:\n").append(block.question()).append("\n\n");
        builder.append("답변:\n").append(block.answer());
        if (block.keywords() != null && !block.keywords().isEmpty()) {
            builder.append("\n\n키워드:\n").append(String.join(", ", block.keywords()));
        }
        return builder.toString();
    }

    private List<String> blockIds(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::blockIds)
                .flatMap(List::stream)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String effectiveSourceDocumentId(NormalizedDocument document, ChunkingContext context) {
        if (!document.sourceDocumentId().isBlank()) {
            return document.sourceDocumentId();
        }
        return context.sourceDocumentId();
    }

    private String sectionId(String sourceDocumentId, int index) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-section-" + index;
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-blockify-" + order;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        metadata.put(key, value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Section(String sectionId, String headingPath, int startOrder, int endOrder,
                           List<NormalizedBlock> blocks) {
    }
}
