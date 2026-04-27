package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.IndexRequest;
import studio.one.platform.ai.web.dto.SearchRequest;

class RagControllerTest {

    @Test
    void indexKeepsAcceptedEmptyBodyAndAddsJobHeaderWhenJobServiceExists() {
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        RagIndexJobService jobService = mock(RagIndexJobService.class);
        RagController controller = new RagController(ragPipelineService, jobService);
        ArgumentCaptor<RagIndexJobCreateRequest> captor = ArgumentCaptor.forClass(RagIndexJobCreateRequest.class);
        when(jobService.createJob(any(RagIndexJobCreateRequest.class)))
                .thenReturn(RagIndexJob.pending(
                        "job-1",
                        "attachment",
                        "42",
                        "doc-1",
                        "raw",
                        java.time.Instant.parse("2026-04-26T00:00:00Z")));
        when(jobService.startJob("job-1")).thenReturn(RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "raw",
                java.time.Instant.parse("2026-04-26T00:00:00Z")));

        ResponseEntity<Void> response = controller.index(new IndexRequest(
                "doc-1",
                "hello",
                Map.of("objectType", "attachment", "objectId", "42"),
                List.of(),
                false));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getFirst(RagController.RAG_JOB_ID_HEADER)).isEqualTo("job-1");
        verify(jobService).createJob(captor.capture());
        verify(jobService).startJob("job-1");
        assertThat(captor.getValue().indexRequest().documentId()).isEqualTo("doc-1");
    }

    @Test
    void searchMapsObjectFilterToCoreRequest() {
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        RagController controller = new RagController(ragPipelineService);
        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        when(ragPipelineService.search(any(RagSearchRequest.class)))
                .thenReturn(List.of(new RagSearchResult("doc-1", "chunk", Map.of(), 0.9d)));

        controller.search(new SearchRequest("hello", 3, "attachment", "42"));

        verify(ragPipelineService).search(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("hello");
        assertThat(captor.getValue().topK()).isEqualTo(3);
        assertThat(captor.getValue().metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(captor.getValue().metadataFilter().objectId()).isEqualTo("42");
    }
}
