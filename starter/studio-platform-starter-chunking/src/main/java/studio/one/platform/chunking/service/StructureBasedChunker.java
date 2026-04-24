package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.core.NormalizedDocumentChunker;

public class StructureBasedChunker implements NormalizedDocumentChunker {

    private final int defaultMaxSize;

    private final int defaultOverlap;

    private final Chunker fallbackChunker;

    public StructureBasedChunker(int defaultMaxSize, int defaultOverlap, Chunker fallbackChunker) {
        if (defaultMaxSize <= 0) {
            throw new IllegalArgumentException("defaultMaxSize must be positive");
        }
        if (defaultOverlap < 0) {
            throw new IllegalArgumentException("defaultOverlap cannot be negative");
        }
        this.defaultMaxSize = defaultMaxSize;
        this.defaultOverlap = Math.min(defaultOverlap, defaultMaxSize - 1);
        this.fallbackChunker = fallbackChunker;
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.STRUCTURE_BASED;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        if (context.text() == null || context.text().isBlank()) {
            return List.of();
        }
        return fallbackChunker.chunk(context);
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document, ChunkingContext context) {
        if (document.blocks().isEmpty()) {
            return fallbackChunker.chunk(context);
        }

        int maxSize = ChunkSizing.effectiveMaxSize(context.maxSize(), defaultMaxSize);
        int overlap = ChunkSizing.effectiveOverlap(context.overlap(), defaultOverlap, maxSize);
        ChunkUnit unit = context.unit();
        List<Section> sections = splitSections(document.blocks());
        List<Chunk> chunks = new ArrayList<>();
        int childOrder = 0;

        for (Section section : sections) {
            ParentChunk parentChunk = createParentChunk(document, context, section, maxSize, overlap, unit);
            childOrder = appendChildChunks(document, context, section, parentChunk, maxSize, overlap, unit, chunks, childOrder);
        }
        return linkNeighborsWithinParent(chunks);
    }

    private ParentChunk createParentChunk(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            int maxSize,
            int overlap,
            ChunkUnit unit) {
        String content = content(section.parentBlocks());
        Map<String, Object> attributes = attributes(document, section.parentBlocks(), section.headingPath(),
                maxSize, overlap, unit, ChunkType.PARENT, null);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), section.startOrder())
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .chunkType(resolveParentChunkType(section))
                .section(section.headingPath())
                .objectType(context.objectType())
                .objectId(context.objectId())
                .charCount(content.length())
                .tokenCount(ChunkSizing.estimateTokens(content))
                .blockIds(collectBlockIds(section.parentBlocks()))
                .confidence(aggregateConfidence(section.parentBlocks()))
                .attributes(attributes)
                .build();
        return new ParentChunk(parentChunkId(effectiveSourceDocumentId(document, context), section.startOrder()),
                content, metadata, section);
    }

    private int appendChildChunks(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            ParentChunk parentChunk,
            int maxSize,
            int overlap,
            ChunkUnit unit,
            List<Chunk> chunks,
            int initialOrder) {
        List<NormalizedBlock> current = new ArrayList<>();
        int order = initialOrder;

        for (NormalizedBlock block : section.blocks()) {
            if (isStandalone(block)) {
                if (!current.isEmpty()) {
                    chunks.add(toChildChunk(document, context, current, order++, section.headingPath(),
                            parentChunk, maxSize, overlap, unit));
                    current = new ArrayList<>();
                }
                chunks.add(toChildChunk(document, context, List.of(block), order++, section.headingPath(),
                        parentChunk, maxSize, overlap, unit));
                continue;
            }

            if (!current.isEmpty() && sizeOf(current, block, unit) > maxSize) {
                chunks.add(toChildChunk(document, context, current, order++, section.headingPath(),
                        parentChunk, maxSize, overlap, unit));
                current = overlapTail(current, overlap, unit);
            }
            if (current.isEmpty()) {
                current = new ArrayList<>();
            }
            current.add(block);
        }

        if (!current.isEmpty()) {
            chunks.add(toChildChunk(document, context, current, order++, section.headingPath(),
                    parentChunk, maxSize, overlap, unit));
        }
        return order;
    }

    private Chunk toChildChunk(
            NormalizedDocument document,
            ChunkingContext context,
            List<NormalizedBlock> blocks,
            int order,
            String headingPath,
            ParentChunk parentChunk,
            int maxSize,
            int overlap,
            ChunkUnit unit) {
        String content = content(blocks);
        Map<String, Object> attributes = attributes(document, blocks, headingPath, maxSize, overlap, unit,
                resolveChunkType(blocks), parentChunk);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .chunkType(resolveChunkType(blocks))
                .parentChunkId(parentChunk.id())
                .section(headingPath)
                .objectType(context.objectType())
                .objectId(context.objectId())
                .charCount(content.length())
                .tokenCount(ChunkSizing.estimateTokens(content))
                .blockIds(collectBlockIds(blocks))
                .confidence(aggregateConfidence(blocks))
                .attributes(attributes)
                .build();
        return Chunk.of(chunkId(effectiveSourceDocumentId(document, context), order), content, metadata);
    }

    private Map<String, Object> attributes(
            NormalizedDocument document,
            List<NormalizedBlock> blocks,
            String headingPath,
            int maxSize,
            int overlap,
            ChunkUnit unit,
            ChunkType chunkType,
            ParentChunk parentChunk) {
        Map<String, Object> attributes = new LinkedHashMap<>(document.metadata());
        NormalizedBlock first = blocks.get(0);
        putIfPresent(attributes, ChunkMetadata.KEY_SOURCE_FORMAT, document.sourceFormat());
        putIfPresent(attributes, ChunkMetadata.KEY_SOURCE_REF, first.effectiveSourceRef());
        putIfPresent(attributes, ChunkMetadata.KEY_BLOCK_TYPE, first.type().name());
        putIfPresent(attributes, ChunkMetadata.KEY_PAGE, first.page());
        putIfPresent(attributes, ChunkMetadata.KEY_SLIDE, first.slide());
        putIfPresent(attributes, ChunkMetadata.KEY_PARENT_BLOCK_ID, first.parentBlockId());
        putIfPresent(attributes, ChunkMetadata.KEY_HEADING_PATH, headingPath);
        putIfPresent(attributes, ChunkMetadata.KEY_TOKEN_ESTIMATE, ChunkSizing.estimateTokens(content(blocks)));
        putIfPresent(attributes, ChunkMetadata.KEY_CHUNK_UNIT, unit.value());
        putIfPresent(attributes, ChunkMetadata.KEY_CHUNK_TYPE, chunkType.value());
        putIfPresent(attributes, ChunkMetadata.KEY_MAX_SIZE, maxSize);
        putIfPresent(attributes, ChunkMetadata.KEY_OVERLAP, overlap);
        putIfPresent(attributes, ChunkMetadata.KEY_SOURCE_REFS, blocks.stream()
                .map(NormalizedBlock::effectiveSourceRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .toList());
        if (parentChunk != null) {
            putIfPresent(attributes, ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, parentChunk.content());
            putIfPresent(attributes, ChunkMetadata.KEY_PARENT_CHUNK_BLOCK_IDS, parentChunk.metadata().blockIds());
            putIfPresent(attributes, ChunkMetadata.KEY_PARENT_CHUNK_SOURCE_REFS,
                    parentChunk.metadata().toMap().get(ChunkMetadata.KEY_SOURCE_REFS));
        }
        return attributes;
    }

    private void putIfPresent(Map<String, Object> attributes, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        attributes.put(key, value);
    }

    private String content(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private int sizeOf(List<NormalizedBlock> current, NormalizedBlock next, ChunkUnit unit) {
        int size = ChunkSizing.sizeOf(content(current), unit);
        if (size > 0) {
            size += unit == ChunkUnit.TOKEN ? 1 : 2;
        }
        return size + ChunkSizing.sizeOf(next.text(), unit);
    }

    private List<NormalizedBlock> overlapTail(List<NormalizedBlock> blocks, int overlap, ChunkUnit unit) {
        if (overlap <= 0 || blocks.isEmpty()) {
            return new ArrayList<>();
        }
        List<NormalizedBlock> tail = new ArrayList<>();
        int size = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            NormalizedBlock block = blocks.get(i);
            int blockSize = ChunkSizing.sizeOf(block.text(), unit);
            if (isBoundary(block) || size + blockSize > overlap) {
                break;
            }
            tail.add(0, block);
            size += blockSize;
        }
        return tail;
    }

    private List<Section> splitSections(List<NormalizedBlock> blocks) {
        List<Section> sections = new ArrayList<>();
        List<NormalizedBlock> current = new ArrayList<>();
        List<NormalizedBlock> currentParentBlocks = new ArrayList<>();
        String headingPath = "";
        int startOrder = 0;

        for (NormalizedBlock block : blocks) {
            if (isHeading(block)) {
                if (!current.isEmpty()) {
                    sections.add(new Section(headingPath, startOrder, List.copyOf(current), List.copyOf(currentParentBlocks)));
                }
                current = new ArrayList<>();
                currentParentBlocks = new ArrayList<>();
                headingPath = resolveHeadingPath(block);
                startOrder = block.order() == null ? sections.size() : block.order();
                currentParentBlocks.add(block);
                continue;
            }
            if (current.isEmpty() && currentParentBlocks.isEmpty()) {
                startOrder = block.order() == null ? sections.size() : block.order();
            }
            current.add(block);
            currentParentBlocks.add(block);
        }

        if (!current.isEmpty() || !currentParentBlocks.isEmpty()) {
            sections.add(new Section(headingPath, startOrder, List.copyOf(current), List.copyOf(currentParentBlocks)));
        }
        return sections;
    }

    private boolean isHeading(NormalizedBlock block) {
        return block.type() == NormalizedBlockType.TITLE || block.type() == NormalizedBlockType.HEADING;
    }

    private boolean isStandalone(NormalizedBlock block) {
        return block.type() == NormalizedBlockType.TABLE
                || block.type() == NormalizedBlockType.IMAGE
                || block.type() == NormalizedBlockType.IMAGE_CAPTION
                || block.type() == NormalizedBlockType.OCR_TEXT;
    }

    private boolean isBoundary(NormalizedBlock block) {
        return isHeading(block) || isStandalone(block);
    }

    private String resolveHeadingPath(NormalizedBlock block) {
        if (block.headingPath() != null && !block.headingPath().isBlank()) {
            return block.headingPath();
        }
        return block.text();
    }

    private String effectiveSourceDocumentId(NormalizedDocument document, ChunkingContext context) {
        if (!document.sourceDocumentId().isBlank()) {
            return document.sourceDocumentId();
        }
        return context.sourceDocumentId();
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-" + order;
    }

    private String parentChunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-parent-" + order;
    }

    private ChunkType resolveChunkType(List<NormalizedBlock> blocks) {
        if (blocks.size() == 1) {
            NormalizedBlockType type = blocks.get(0).type();
            if (type == NormalizedBlockType.TABLE) {
                return ChunkType.TABLE;
            }
            if (type == NormalizedBlockType.OCR_TEXT) {
                return ChunkType.OCR;
            }
            if (type == NormalizedBlockType.IMAGE || type == NormalizedBlockType.IMAGE_CAPTION) {
                return ChunkType.IMAGE_CAPTION;
            }
            if (type == NormalizedBlockType.PAGE) {
                return ChunkType.SLIDE;
            }
        }
        return ChunkType.CHILD;
    }

    private ChunkType resolveParentChunkType(Section section) {
        if (section.parentBlocks().stream().anyMatch(block -> block.slide() != null && block.page() == null)) {
            return ChunkType.SLIDE;
        }
        return ChunkType.PARENT;
    }

    private List<String> collectBlockIds(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::blockIds)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    }

    private Double aggregateConfidence(List<NormalizedBlock> blocks) {
        List<Double> values = blocks.stream()
                .map(NormalizedBlock::confidence)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private List<Chunk> linkNeighborsWithinParent(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<Chunk> linked = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            Chunk previous = i == 0 ? null : chunks.get(i - 1);
            Chunk next = i == chunks.size() - 1 ? null : chunks.get(i + 1);
            ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                    .sourceDocumentId(chunk.metadata().sourceDocumentId())
                    .parentId(chunk.metadata().parentId())
                    .chunkType(chunk.metadata().chunkType())
                    .parentChunkId(chunk.metadata().parentChunkId())
                    .previousChunkId(sameParent(chunk, previous) ? previous.id() : null)
                    .nextChunkId(sameParent(chunk, next) ? next.id() : null)
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

    private boolean sameParent(Chunk current, Chunk other) {
        return other != null && Objects.equals(current.metadata().parentChunkId(), other.metadata().parentChunkId());
    }

    private record Section(String headingPath, int startOrder, List<NormalizedBlock> blocks,
                           List<NormalizedBlock> parentBlocks) {
    }

    private record ParentChunk(String id, String content, ChunkMetadata metadata, Section section) {
    }
}
