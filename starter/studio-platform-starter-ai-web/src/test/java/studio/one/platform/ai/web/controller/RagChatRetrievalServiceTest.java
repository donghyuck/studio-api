package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.chunking.core.ChunkMetadata;

class RagChatRetrievalServiceTest {

    @Mock
    private RagPipelineService ragPipelineService;

    private RagChatRetrievalService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RagChatRetrievalService(ragPipelineService, new AiWebRagProperties.RetrievalProperties());
    }

    @Test
    void explicitDefaultStrategyUsesSingleLegacySearch() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(result("doc-1", "chunk-1", 0.9d)));

        RagChatRetrievalService.RetrievalResult result = service.retrieve(
                request("default", null),
                "query",
                "attachment",
                "1",
                3,
                0.6d,
                3,
                true);

        assertThat(result.results()).hasSize(1);
        assertThat(result.debug().enabled()).isFalse();
        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(ragPipelineService).search(captor.capture());
        assertThat(captor.getValue().metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(captor.getValue().metadataFilter().objectId()).isEqualTo("1");
        assertThat(captor.getValue().metadataFilter().equalsCriteria()).doesNotContainKey(ChunkMetadata.KEY_STRATEGY);
    }

    @Test
    void omittedStrategyUsesConfiguredHybridDefault() {
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of());

        service.retrieve(request(null, null), "query", "attachment", "1", 5, 0.6d, 5, true);

        verify(ragPipelineService, times(3)).search(any(RagSearchRequest.class));
    }

    @Test
    void structureStrategyAddsStructureBasedFilter() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(result("doc-1", "chunk-1", 0.9d)));

        service.retrieve(request("structure", null), "query", "attachment", "1", 5, 0.6d, 5, true);

        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(ragPipelineService).search(captor.capture());
        assertThat(captor.getValue().metadataFilter().equalsCriteria())
                .containsEntry(ChunkMetadata.KEY_STRATEGY, "structure-based")
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "1");
    }

    @Test
    void ideaBlockStrategySearchesBlockifyAndIdeaBlockLegs() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(result("doc-1", "chunk-1", 0.9d)))
                .thenReturn(List.of(result("doc-2", "chunk-2", 0.8d)));

        RagChatRetrievalService.RetrievalResult result = service.retrieve(
                request("ideaBlock", new ChatRagRetrievalOptionsDto(null, 7, 4, null, true, true, null, null)),
                "query",
                "attachment",
                "1",
                5,
                0.6d,
                5,
                true);

        assertThat(result.results()).extracting(RagSearchResult::documentId).containsExactly("doc-1", "doc-2");
        assertThat(result.debug().toMetadata())
                .containsEntry("requestedStrategy", "ideaBlock")
                .containsEntry("resolvedStrategy", "ideaBlock");
        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(ragPipelineService, times(2)).search(captor.capture());
        assertThat(captor.getAllValues().get(0).metadataFilter().equalsCriteria())
                .containsEntry("actualChunkingStrategy", "blockify");
        assertThat(captor.getAllValues().get(1).metadataFilter().equalsCriteria())
                .containsEntry(ChunkMetadata.KEY_CHUNK_TYPE, "ideaBlock");
    }

    @Test
    void hybridStrategyMergesAndDedupesByChunkId() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(result("structure", "same", 0.7d)))
                .thenReturn(List.of(result("blockify", "same", 0.95d)))
                .thenReturn(List.of(result("idea", "other", 0.8d)));

        RagChatRetrievalService.RetrievalResult result = service.retrieve(
                request("hybrid", new ChatRagRetrievalOptionsDto(3, 3, 2, null, true, true, null, null)),
                "query",
                "attachment",
                "1",
                5,
                0.6d,
                5,
                true);

        assertThat(result.results()).extracting(RagSearchResult::documentId)
                .containsExactly("blockify", "idea");
        Map<String, Object> debug = result.debug().toMetadata();
        assertThat(debug)
                .containsEntry("requestedStrategy", "hybrid")
                .containsEntry("resolvedStrategy", "hybrid")
                .containsEntry("finalCount", 2);
        assertThat((List<?>) debug.get("legs")).hasSize(3);
        assertThat((List<?>) debug.get("chunks")).hasSize(2);
    }

    @Test
    void hybridStrategyCanBoostDistilledIdeaBlockRanking() {
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(result("structure", "structure", 0.91d)))
                .thenReturn(List.of(result("distilled", "distilled", 0.85d,
                        Map.of(RagContextBuilder.KEY_CHUNK_ID, "distilled", "distilled", true))))
                .thenReturn(List.of());

        RagChatRetrievalService.RetrievalResult result = service.retrieve(
                request("hybrid", new ChatRagRetrievalOptionsDto(3, 3, 2, null, true, true, 0.1d, null)),
                "query",
                "attachment",
                "1",
                5,
                0.6d,
                5,
                true);

        assertThat(result.results()).extracting(RagSearchResult::documentId)
                .containsExactly("distilled", "structure");
    }

    @Test
    void retrievalOptionsCanDisableQueryExpansion() {
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of());

        service.retrieve(
                request("structure", new ChatRagRetrievalOptionsDto(null, null, null, null, null, null, null, false)),
                "query",
                "attachment",
                "1",
                5,
                0.6d,
                5,
                true);

        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        verify(ragPipelineService).search(captor.capture());
        assertThat(captor.getValue().queryExpansionEnabled()).isFalse();
    }

    @Test
    void autoStrategyResolvesToHybrid() {
        when(ragPipelineService.search(any(RagSearchRequest.class))).thenReturn(List.of());

        RagChatRetrievalService.RetrievalResult result = service.retrieve(
                request("auto", null),
                "query",
                null,
                null,
                5,
                0.6d,
                5,
                true);

        assertThat(result.debug().toMetadata())
                .containsEntry("requestedStrategy", "auto")
                .containsEntry("resolvedStrategy", "hybrid");
        verify(ragPipelineService, times(3)).search(any(RagSearchRequest.class));
    }

    private ChatRagRequestDto request(String strategy, ChatRagRetrievalOptionsDto options) {
        return new ChatRagRequestDto(
                new ChatRequestDto(
                        null,
                        null,
                        List.of(new ChatMessageDto("user", "question")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "query",
                5,
                "attachment",
                "1",
                null,
                null,
                null,
                5,
                null,
                true,
                strategy,
                options);
    }

    private RagSearchResult result(String documentId, String chunkId, double score) {
        return new RagSearchResult(documentId, "content " + documentId, Map.of(RagContextBuilder.KEY_CHUNK_ID, chunkId),
                score);
    }

    private RagSearchResult result(String documentId, String chunkId, double score, Map<String, Object> metadata) {
        return new RagSearchResult(documentId, "content " + documentId, metadata, score);
    }
}
