package studio.one.platform.chunking.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChunkSetTest {

    @Test
    void preservesOrderedChunkItemsAndQualityMetadata() {
        ChunkSet chunkSet = chunkSet(List.of(
                new ChunkSetItem(1, "chunk-2", "둘", "hash-2", Map.of("page", 2)),
                new ChunkSetItem(0, "chunk-1", "하나", "hash-1", Map.of("page", 1))));

        assertThat(chunkSet.items()).extracting(ChunkSetItem::chunkId)
                .containsExactly("chunk-1", "chunk-2");
        assertThat(chunkSet.indexEligible()).isTrue();
    }

    @Test
    void rejectsNonContiguousIndexes() {
        assertThatThrownBy(() -> chunkSet(List.of(
                new ChunkSetItem(1, "chunk-2", "둘", "hash-2", Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contiguous");
    }

    @Test
    void blocksExplicitlyIneligibleChunkSet() {
        ChunkSet source = chunkSet(List.of(
                new ChunkSetItem(0, "chunk-1", "하나", "hash-1", Map.of())));
        ChunkSet blocked = new ChunkSet(
                source.chunkSetId(), source.objectType(), source.objectId(), source.documentId(),
                source.sourceRevisionId(), source.sourceContentHash(), source.strategy(), source.strategyHash(),
                source.chunkUnit(), source.maxSize(), source.overlap(), source.status(),
                ChunkSetQualityStatus.REVIEW_REQUIRED, List.of("QUALITY_GATE_FAILED"),
                Map.of("ragIndexEligible", false), source.items(), source.createdAt(), source.updatedAt());

        assertThat(blocked.indexEligible()).isFalse();
    }

    private ChunkSet chunkSet(List<ChunkSetItem> items) {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        return new ChunkSet(
                "cset-1", "attachment", "6", "mdoc-1", "mrev-1", "source-hash",
                "structure-based", "strategy-hash", "CHARACTER", 1200, 150,
                ChunkSetStatus.READY, ChunkSetQualityStatus.VALID, List.of(), Map.of(), items, now, now);
    }
}
