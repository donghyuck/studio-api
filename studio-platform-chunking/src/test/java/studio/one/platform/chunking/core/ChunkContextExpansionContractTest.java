package studio.one.platform.chunking.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChunkContextExpansionContractTest {

    @Test
    void requestSanitizesChunksAndOptions() {
        Chunk seed = chunk("doc-1", "seed", 0);
        Chunk sibling = chunk("doc-2", "sibling", 1);

        ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(seed)
                .availableChunks(Arrays.asList(seed, null, sibling))
                .previousWindow(1)
                .nextWindow(2)
                .options(Map.of("mode", "parent", "blank", " "))
                .build();

        assertThat(request.availableChunks()).containsExactly(seed, sibling);
        assertThat(request.option("mode")).contains("parent");
        assertThat(request.option("blank")).isEmpty();
        assertThat(request.chunkById("doc-2")).contains(sibling);
        assertThat(request.includeParentContent()).isTrue();
    }

    @Test
    void requestRejectsNegativeWindow() {
        Chunk seed = chunk("doc-1", "seed", 0);

        assertThatThrownBy(() -> ChunkContextExpansionRequest.builder(seed).previousWindow(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previousWindow");
    }

    @Test
    void expansionJoinsContextChunksAndKeepsSeed() {
        Chunk seed = chunk("doc-1", "seed", 0);
        Chunk next = chunk("doc-2", "next", 1);

        ChunkContextExpansion expansion = ChunkContextExpansion.of(
                seed,
                List.of(seed, next),
                ChunkContextExpansionStrategy.WINDOW,
                Map.of("source", "test"));

        assertThat(expansion.seedChunk()).isSameAs(seed);
        assertThat(expansion.contextChunks()).containsExactly(seed, next);
        assertThat(expansion.content()).isEqualTo("seed\n\nnext");
        assertThat(expansion.strategy()).isEqualTo(ChunkContextExpansionStrategy.WINDOW);
        assertThat(expansion.metadata()).containsEntry("source", "test");
    }

    @Test
    void expansionFactoryRejectsNullSeed() {
        assertThatThrownBy(() -> ChunkContextExpansion.of(null, List.of(), ChunkContextExpansionStrategy.WINDOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("seedChunk");
    }

    @Test
    void strategyFromFallsBackToUnknownForPersistenceSafety() {
        assertThat(ChunkContextExpansionStrategy.from("parent-child"))
                .isEqualTo(ChunkContextExpansionStrategy.PARENT_CHILD);
        assertThat(ChunkContextExpansionStrategy.from("unexpected"))
                .isEqualTo(ChunkContextExpansionStrategy.UNKNOWN);
    }

    private Chunk chunk(String id, String content, int order) {
        return Chunk.of(id, content, ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, order).build());
    }
}
