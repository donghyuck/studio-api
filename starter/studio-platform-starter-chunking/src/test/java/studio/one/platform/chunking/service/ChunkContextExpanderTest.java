package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingStrategyType;

class ChunkContextExpanderTest {

    @Test
    void windowExpansionFollowsPreviousAndNextLinksWithinAvailableChunks() {
        Chunk first = chunk("doc-0", "first", 0, "parent", null, "doc-1", "Install", ChunkType.CHILD,
                Map.of());
        Chunk seed = chunk("doc-1", "seed", 1, "parent", "doc-0", "doc-2", "Install", ChunkType.CHILD,
                Map.of());
        Chunk last = chunk("doc-2", "last", 2, "parent", "doc-1", null, "Install", ChunkType.CHILD,
                Map.of());

        ChunkContextExpansion expansion = new WindowChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(last, seed, first))
                        .previousWindow(1)
                        .nextWindow(1)
                        .build());

        assertThat(expansion.strategy()).isEqualTo(ChunkContextExpansionStrategy.WINDOW);
        assertThat(expansion.contextChunks()).containsExactly(first, seed, last);
        assertThat(expansion.content()).isEqualTo("first\n\nseed\n\nlast");
        assertThat(expansion.metadata()).containsEntry("previousCount", 1).containsEntry("nextCount", 1);
    }

    @Test
    void windowExpansionStopsWhenLinksCycleBackToSeed() {
        Chunk seed = chunk("doc-1", "seed", 1, "parent", "doc-0", null, "Install", ChunkType.CHILD,
                Map.of());
        Chunk previous = chunk("doc-0", "previous", 0, "parent", "doc-1", null, "Install", ChunkType.CHILD,
                Map.of());

        ChunkContextExpansion expansion = new WindowChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(previous, seed))
                        .previousWindow(3)
                        .build());

        assertThat(expansion.contextChunks()).containsExactly(previous, seed);
        assertThat(expansion.content()).isEqualTo("previous\n\nseed");
    }

    @Test
    void windowExpansionStopsWhenLinksCycleOutsideSeed() {
        Chunk seed = chunk("doc-1", "seed", 1, "parent", null, "doc-2", "Install", ChunkType.CHILD,
                Map.of());
        Chunk next = chunk("doc-2", "next", 2, "parent", null, "doc-3", "Install", ChunkType.CHILD,
                Map.of());
        Chunk cycle = chunk("doc-3", "cycle", 3, "parent", null, "doc-2", "Install", ChunkType.CHILD,
                Map.of());

        ChunkContextExpansion expansion = new WindowChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(seed, next, cycle))
                        .nextWindow(5)
                        .build());

        assertThat(expansion.contextChunks()).containsExactly(seed, next, cycle);
        assertThat(expansion.content()).isEqualTo("seed\n\nnext\n\ncycle");
    }

    @Test
    void parentChildExpansionPrefersStoredParentContentAndKeepsSiblingOrder() {
        Chunk seed = chunk("doc-1", "child", 1, "parent", null, null, "Install", ChunkType.CHILD,
                Map.of(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Install\n\nchild\n\nnext"));
        Chunk next = chunk("doc-2", "next", 2, "parent", null, null, "Install", ChunkType.CHILD,
                Map.of(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Install\n\nchild\n\nnext"));

        ChunkContextExpansion expansion = new ParentChildChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(next, seed))
                        .build());

        assertThat(expansion.strategy()).isEqualTo(ChunkContextExpansionStrategy.PARENT_CHILD);
        assertThat(expansion.contextChunks()).containsExactly(seed, next);
        assertThat(expansion.content()).isEqualTo("Install\n\nchild\n\nnext");
        assertThat(expansion.metadata()).containsEntry(ChunkMetadata.KEY_PARENT_CHUNK_ID, "parent");
    }

    @Test
    void parentChildExpansionDeduplicatesAvailableChunkWithSameSeedId() {
        Chunk staleSeed = chunk("doc-1", "stale", 1, "parent", null, null, "Install", ChunkType.CHILD,
                Map.of());
        Chunk seed = chunk("doc-1", "fresh", 1, "parent", null, null, "Install", ChunkType.CHILD,
                Map.of());
        Chunk next = chunk("doc-2", "next", 2, "parent", null, null, "Install", ChunkType.CHILD,
                Map.of());

        ChunkContextExpansion expansion = new ParentChildChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(staleSeed, next))
                        .includeParentContent(false)
                        .build());

        assertThat(expansion.contextChunks()).containsExactly(seed, next);
        assertThat(expansion.content()).isEqualTo("fresh\n\nnext");
    }

    @Test
    void headingExpansionUsesSameSectionCandidates() {
        Chunk seed = chunk("doc-1", "seed", 1, "parent-a", null, null, "Install", ChunkType.CHILD, Map.of());
        Chunk sameHeading = chunk("doc-2", "same", 2, "parent-a", null, null, "Install", ChunkType.CHILD, Map.of());
        Chunk otherHeading = chunk("doc-3", "other", 3, "parent-b", null, null, "Operate", ChunkType.CHILD, Map.of());

        ChunkContextExpansion expansion = new HeadingChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(seed)
                        .availableChunks(List.of(otherHeading, sameHeading, seed))
                        .build());

        assertThat(expansion.strategy()).isEqualTo(ChunkContextExpansionStrategy.HEADING);
        assertThat(expansion.contextChunks()).containsExactly(seed, sameHeading);
        assertThat(expansion.content()).isEqualTo("seed\n\nsame");
        assertThat(expansion.metadata()).containsEntry(ChunkMetadata.KEY_SECTION, "Install");
    }

    @Test
    void tableExpansionKeepsTableAsSeedAndCanUseParentContent() {
        Chunk table = chunk("doc-table", "Name: Alice", 0, "parent", null, null, "Metrics", ChunkType.TABLE,
                Map.of(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT, "Metrics\n\nName: Alice"));

        ChunkContextExpansion expansion = new TableChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(table).build());

        assertThat(expansion.strategy()).isEqualTo(ChunkContextExpansionStrategy.TABLE);
        assertThat(expansion.contextChunks()).containsExactly(table);
        assertThat(expansion.content()).isEqualTo("Metrics\n\nName: Alice");
        assertThat(expansion.metadata()).containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "table");
    }

    @Test
    void tableExpansionReturnsNonTableSeedUnchanged() {
        Chunk paragraph = chunk("doc-1", "Body", 0, "parent", null, null, "Install", ChunkType.CHILD, Map.of());

        ChunkContextExpansion expansion = new TableChunkContextExpander().expand(
                ChunkContextExpansionRequest.builder(paragraph).build());

        assertThat(expansion.contextChunks()).containsExactly(paragraph);
        assertThat(expansion.content()).isEqualTo("Body");
        assertThat(expansion.metadata()).containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "child");
    }

    private Chunk chunk(
            String id,
            String content,
            int order,
            String parentChunkId,
            String previousChunkId,
            String nextChunkId,
            String section,
            ChunkType chunkType,
            Map<String, Object> attributes) {
        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, order)
                .sourceDocumentId("doc")
                .chunkType(chunkType)
                .parentChunkId(parentChunkId)
                .previousChunkId(previousChunkId)
                .nextChunkId(nextChunkId)
                .section(section)
                .attributes(attributes)
                .build();
        return Chunk.of(id, content, metadata);
    }
}
