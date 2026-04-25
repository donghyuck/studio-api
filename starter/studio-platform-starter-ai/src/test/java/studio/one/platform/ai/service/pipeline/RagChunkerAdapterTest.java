package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

@SuppressWarnings("deprecation")
class RagChunkerAdapterTest {

    @Test
    void legacyAdapterConvertsTextChunksWithoutMetadata() {
        LegacyTextChunkerAdapter adapter = new LegacyTextChunkerAdapter(
                (documentId, text) -> List.of(new TextChunk(documentId + "-0", text)));

        List<RagPipelineChunk> chunks = adapter.chunk(
                "hello world",
                new RagIndexRequest("doc-1", "hello world", Map.of()));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).id()).isEqualTo("doc-1-0");
        assertThat(chunks.get(0).content()).isEqualTo("hello world");
        assertThat(chunks.get(0).metadata()).isEmpty();
    }

    @Test
    void orchestratedAdapterBuildsChunkingContextAndPreservesMetadata() {
        AtomicReference<ChunkingContext> capturedContext = new AtomicReference<>();
        OrchestratedRagChunker adapter = new OrchestratedRagChunker(context -> {
            capturedContext.set(context);
            return List.of(Chunk.of(
                    "chunk-1",
                    context.text(),
                    ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 3)
                            .sourceDocumentId(context.sourceDocumentId())
                            .objectType(context.objectType())
                            .objectId(context.objectId())
                            .build()));
        });
        RagIndexRequest request = new RagIndexRequest("doc-1", "ignored", Map.of(
                "objectType", "attachment",
                "objectId", "42",
                "filename", "file.pdf"));

        List<RagPipelineChunk> chunks = adapter.chunk("indexed text", request);

        assertThat(capturedContext.get().sourceDocumentId()).isEqualTo("doc-1");
        assertThat(capturedContext.get().objectType()).isEqualTo("attachment");
        assertThat(capturedContext.get().objectId()).isEqualTo("42");
        assertThat(capturedContext.get().filename()).isEqualTo("file.pdf");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata())
                .containsEntry("sourceDocumentId", "doc-1")
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "42")
                .containsEntry("strategy", "recursive")
                .containsEntry("chunkOrder", 3);
    }
}
