package studio.one.platform.markdown.autoconfigure;

import java.util.List;

import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

final class AutomaticChunkingStrategyResolver {

    static final String KEY_SELECTION_MODE = "chunkingStrategySelectionMode";
    static final String KEY_SELECTION_REASON = "chunkingStrategySelectionReason";
    static final String KEY_SELECTED_STRATEGY = "selectedChunkingStrategy";
    static final String KEY_STRUCTURED_BLOCK_COUNT = "structuredBlockCount";

    Selection resolve(NormalizedDocument document, String requestedStrategy) {
        if (requestedStrategy != null && !requestedStrategy.isBlank()) {
            return new Selection(
                    ChunkingStrategyType.from(requestedStrategy),
                    "EXPLICIT",
                    "EXPLICIT_REQUEST",
                    structuredBlockCount(document));
        }

        List<NormalizedBlock> contentBlocks = contentBlocks(document);
        int structuredBlockCount = structuredBlockCount(document);
        if (hasLayoutBoundary(contentBlocks) || hasStructuredSequence(contentBlocks)) {
            return new Selection(
                    ChunkingStrategyType.STRUCTURE_BASED,
                    "AUTO",
                    "STRUCTURED_BLOCKS_AVAILABLE",
                    structuredBlockCount);
        }
        return new Selection(
                ChunkingStrategyType.RECURSIVE,
                "AUTO",
                contentBlocks.isEmpty() ? "NO_CONTENT_BLOCKS" : "PLAIN_TEXT_ONLY",
                structuredBlockCount);
    }

    private List<NormalizedBlock> contentBlocks(NormalizedDocument document) {
        if (document == null || document.blocks() == null) {
            return List.of();
        }
        return document.blocks().stream()
                .filter(block -> block.type() != NormalizedBlockType.TITLE)
                .filter(block -> block.type() != NormalizedBlockType.HEADING)
                .filter(block -> block.type() != NormalizedBlockType.HEADER)
                .filter(block -> block.type() != NormalizedBlockType.FOOTER)
                .filter(block -> block.type() != NormalizedBlockType.METADATA)
                .toList();
    }

    private boolean hasLayoutBoundary(List<NormalizedBlock> blocks) {
        return blocks.stream().anyMatch(block -> switch (block.type()) {
            case PAGE, TABLE, TABLE_ROW, TABLE_CELL, IMAGE, IMAGE_CAPTION, FOOTNOTE, OCR_TEXT -> true;
            default -> false;
        });
    }

    private boolean hasStructuredSequence(List<NormalizedBlock> blocks) {
        return blocks.size() > 1 && blocks.stream().anyMatch(block ->
                (block.type() != NormalizedBlockType.DOCUMENT
                        && block.type() != NormalizedBlockType.UNKNOWN)
                        || block.page() != null
                        || block.slide() != null
                        || !block.sourceRef().isBlank()
                        || !block.headingPath().isBlank());
    }

    private int structuredBlockCount(NormalizedDocument document) {
        if (document == null || document.blocks() == null) {
            return 0;
        }
        return (int) document.blocks().stream()
                .filter(block -> block.type() != NormalizedBlockType.DOCUMENT)
                .filter(block -> block.type() != NormalizedBlockType.UNKNOWN)
                .count();
    }

    record Selection(
            ChunkingStrategyType strategy,
            String mode,
            String reason,
            int structuredBlockCount) {
    }
}
