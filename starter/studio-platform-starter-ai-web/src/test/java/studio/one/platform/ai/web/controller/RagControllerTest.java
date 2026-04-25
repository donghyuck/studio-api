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

import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.SearchRequest;

class RagControllerTest {

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
