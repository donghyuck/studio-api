package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;

class RagContextBuilderTest {

    @Test
    void keepsExistingContextWhenNoExpanderIsConfigured() {
        RagContextBuilder builder = new RagContextBuilder(8, 12_000, true);

        String context = builder.build(List.of(result("chunk-1", "seed", metadata("chunk-1"))));

        assertThat(context)
                .contains("docId=chunk-1")
                .contains("score=0.900")
                .contains("seed");
    }

    @Test
    void expandsChunkContextWhenObjectScopedMetadataIsAvailable() {
        RagContextBuilder builder = new RagContextBuilder(8, 12_000, true, TestWindowChunkContextExpander.asList());

        String context = builder.build(
                List.of(result("chunk-2", "seed", metadata("chunk-2"))),
                List.of(
                        result("chunk-1", "previous", metadata("chunk-1", null, "chunk-2", 0)),
                        result("chunk-2", "seed", metadata("chunk-2", "chunk-1", "chunk-3", 1)),
                        result("chunk-3", "next", metadata("chunk-3", "chunk-2", null, 2))));

        assertThat(context)
                .contains("docId=chunk-2")
                .contains("score=0.900")
                .contains("previous\nseed\nnext");
    }

    @Test
    void doesNotExpandWhenObjectScopeIsMissing() {
        CountingExpander expander = new CountingExpander();
        RagContextBuilder builder = new RagContextBuilder(8, 12_000, true, List.of(expander));

        String context = builder.build(List.of(new RagSearchResult(
                "chunk-1",
                "seed",
                Map.of(RagContextBuilder.KEY_CHUNK_ID, "chunk-1"),
                0.9d)));

        assertThat(context).contains("seed");
        assertThat(expander.calls).isZero();
    }

    @Test
    void doesNotExpandWhenExpansionIsDisabled() {
        CountingExpander expander = new CountingExpander();
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        expansion.setEnabled(false);
        RagContextBuilder builder = new RagContextBuilder(8, 12_000, true, expansion, List.of(expander));

        String context = builder.build(List.of(result("chunk-1", "seed", metadata("chunk-1"))));

        assertThat(context).contains("seed");
        assertThat(expander.calls).isZero();
        assertThat(builder.supportsExpansion()).isFalse();
    }

    @Test
    void passesExpansionWindowOptionsToExpander() {
        RecordingExpander expander = new RecordingExpander();
        AiWebRagProperties.ExpansionProperties expansion = new AiWebRagProperties.ExpansionProperties();
        expansion.setPreviousWindow(2);
        expansion.setNextWindow(3);
        expansion.setIncludeParentContent(false);
        RagContextBuilder builder = new RagContextBuilder(8, 12_000, true, expansion, List.of(expander));

        builder.build(List.of(result("chunk-1", "seed", metadata("chunk-1"))));

        assertThat(expander.previousWindow).isEqualTo(2);
        assertThat(expander.nextWindow).isEqualTo(3);
        assertThat(expander.includeParentContent).isFalse();
    }

    @Test
    void keepsCharacterBudgetAfterExpansion() {
        RagContextBuilder builder = new RagContextBuilder(8, 80, true, TestWindowChunkContextExpander.asList());

        String context = builder.build(
                List.of(result("chunk-2", "seed", metadata("chunk-2"))),
                List.of(
                        result("chunk-1", "previous text that makes the expanded content too long",
                                metadata("chunk-1", null, "chunk-2", 0)),
                        result("chunk-2", "seed", metadata("chunk-2", "chunk-1", "chunk-3", 1)),
                        result("chunk-3", "next text that makes the expanded content too long",
                                metadata("chunk-3", "chunk-2", null, 2))));

        assertThat(context).isEqualTo("참고할 문서가 없습니다. 일반적으로 답변하세요.");
    }

    private RagSearchResult result(String chunkId, String content, Map<String, Object> metadata) {
        return new RagSearchResult(chunkId, content, metadata, 0.9d);
    }

    private Map<String, Object> metadata(String chunkId) {
        return metadata(chunkId, "chunk-1", "chunk-3", 1);
    }

    private Map<String, Object> metadata(String chunkId, String previousChunkId, String nextChunkId, int order) {
        return Map.ofEntries(
                Map.entry(ChunkMetadata.KEY_OBJECT_TYPE, "attachment"),
                Map.entry(ChunkMetadata.KEY_OBJECT_ID, "123"),
                Map.entry(RagContextBuilder.KEY_CHUNK_ID, chunkId),
                Map.entry(ChunkMetadata.KEY_CHUNK_ORDER, order),
                Map.entry(ChunkMetadata.KEY_PREVIOUS_CHUNK_ID, previousChunkId == null ? "" : previousChunkId),
                Map.entry(ChunkMetadata.KEY_NEXT_CHUNK_ID, nextChunkId == null ? "" : nextChunkId));
    }

    private static final class CountingExpander implements ChunkContextExpander {
        private int calls;

        @Override
        public ChunkContextExpansionStrategy strategy() {
            return ChunkContextExpansionStrategy.WINDOW;
        }

        @Override
        public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
            calls++;
            return ChunkContextExpansion.of(request.seedChunk(), List.of(request.seedChunk()), strategy());
        }
    }

    private static final class RecordingExpander implements ChunkContextExpander {
        private int previousWindow;
        private int nextWindow;
        private boolean includeParentContent = true;

        @Override
        public ChunkContextExpansionStrategy strategy() {
            return ChunkContextExpansionStrategy.WINDOW;
        }

        @Override
        public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
            previousWindow = request.previousWindow();
            nextWindow = request.nextWindow();
            includeParentContent = request.includeParentContent();
            return ChunkContextExpansion.of(request.seedChunk(), List.of(request.seedChunk()), strategy());
        }
    }
}
