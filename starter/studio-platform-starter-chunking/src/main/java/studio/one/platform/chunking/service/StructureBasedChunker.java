package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
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
        NormalizedDocument document = NormalizedDocument.builder(context.sourceDocumentId())
                .plainText(context.text())
                .sourceFormat(context.contentType())
                .filename(context.filename())
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, context.text())
                        .sourceRef("document")
                        .order(0)
                        .metadata(context.metadata())
                        .build()))
                .metadata(context.metadata())
                .build();
        return chunk(document, context);
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document, ChunkingContext context) {
        if (document.blocks().isEmpty()) {
            return fallbackChunker.chunk(context);
        }

        int maxSize = ChunkSizing.effectiveMaxSize(context.maxSize(), defaultMaxSize);
        int overlap = ChunkSizing.effectiveOverlap(context.overlap(), defaultOverlap, maxSize);
        ChunkUnit unit = context.unit();
        List<Chunk> chunks = new ArrayList<>();
        List<NormalizedBlock> current = new ArrayList<>();
        String headingPath = "";

        for (NormalizedBlock block : document.blocks()) {
            if (isHeading(block)) {
                flush(document, context, chunks, current, headingPath, maxSize, overlap, unit);
                headingPath = block.text();
                current.clear();
                current.add(block);
                continue;
            }

            if (isStandalone(block)) {
                flush(document, context, chunks, current, headingPath, maxSize, overlap, unit);
                current.clear();
                chunks.add(toChunk(document, context, List.of(block), chunks.size(), headingPath, maxSize, overlap, unit));
                continue;
            }

            if (!current.isEmpty() && sizeOf(current, block, unit) > maxSize) {
                flush(document, context, chunks, current, headingPath, maxSize, overlap, unit);
                current = overlapTail(current, overlap, unit);
            }
            current.add(block);
        }

        flush(document, context, chunks, current, headingPath, maxSize, overlap, unit);
        return chunks;
    }

    private void flush(
            NormalizedDocument document,
            ChunkingContext context,
            List<Chunk> chunks,
            List<NormalizedBlock> current,
            String headingPath,
            int maxSize,
            int overlap,
            ChunkUnit unit) {
        if (current.isEmpty()) {
            return;
        }
        chunks.add(toChunk(document, context, current, chunks.size(), headingPath, maxSize, overlap, unit));
    }

    private Chunk toChunk(
            NormalizedDocument document,
            ChunkingContext context,
            List<NormalizedBlock> blocks,
            int order,
            String headingPath,
            int maxSize,
            int overlap,
            ChunkUnit unit) {
        String content = content(blocks);
        Map<String, Object> attributes = attributes(document, blocks, headingPath, maxSize, overlap, unit);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .section(headingPath)
                .objectType(context.objectType())
                .objectId(context.objectId())
                .charCount(content.length())
                .tokenCount(ChunkSizing.estimateTokens(content))
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
            ChunkUnit unit) {
        Map<String, Object> attributes = new LinkedHashMap<>(document.metadata());
        NormalizedBlock first = blocks.get(0);
        attributes.put(ChunkMetadata.KEY_SOURCE_FORMAT, document.sourceFormat());
        attributes.put(ChunkMetadata.KEY_SOURCE_REF, first.effectiveSourceRef());
        attributes.put(ChunkMetadata.KEY_BLOCK_TYPE, first.type().name());
        attributes.put(ChunkMetadata.KEY_PAGE, first.page());
        attributes.put(ChunkMetadata.KEY_SLIDE, first.slide());
        attributes.put(ChunkMetadata.KEY_PARENT_BLOCK_ID, first.parentBlockId());
        attributes.put(ChunkMetadata.KEY_HEADING_PATH, headingPath);
        attributes.put(ChunkMetadata.KEY_TOKEN_ESTIMATE, ChunkSizing.estimateTokens(content(blocks)));
        attributes.put(ChunkMetadata.KEY_CHUNK_UNIT, unit.value());
        attributes.put(ChunkMetadata.KEY_MAX_SIZE, maxSize);
        attributes.put(ChunkMetadata.KEY_OVERLAP, overlap);
        attributes.put(ChunkMetadata.KEY_SOURCE_REFS, blocks.stream()
                .map(NormalizedBlock::effectiveSourceRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .toList());
        return attributes;
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
}
