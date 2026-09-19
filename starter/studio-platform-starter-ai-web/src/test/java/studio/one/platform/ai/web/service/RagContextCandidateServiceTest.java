package studio.one.platform.ai.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

class RagContextCandidateServiceTest {
    private final RagPipelineService pipeline = mock(RagPipelineService.class);
    private final RagContextCandidateService service = new RagContextCandidateService(pipeline, 4, 100);
    private final List<RagSearchResult> original = List.of(new RagSearchResult("doc", "text", Map.of(), 1));

    @Test
    void fetchesOnlyTheSuppliedObjectWithBoundedLimit() {
        when(pipeline.listByObject("attachment", "12", 100)).thenReturn(original);
        assertThat(service.expand(List.of(), "attachment", "12", Integer.MAX_VALUE, false, true)).isSameAs(original);
        verify(pipeline).listByObject("attachment", "12", 100);
    }

    @Test
    void missingScopeUnsupportedOrAlreadyExpandedDoesNotSearch() {
        assertThat(service.expand(original, "attachment", "", 5, false, true)).isSameAs(original);
        assertThat(service.expand(original, "attachment", "12", 5, true, true)).isSameAs(original);
        assertThat(service.expand(original, "attachment", "12", 5, false, false)).isSameAs(original);
        verifyNoInteractions(pipeline);
    }

    @Test
    void emptyAndFailedFetchPreserveOriginalEvidence() {
        when(pipeline.listByObject("attachment", "12", 20)).thenReturn(List.of())
                .thenThrow(new IllegalStateException("unavailable"));
        assertThat(service.expand(original, "attachment", "12", 5, false, true)).isSameAs(original);
        assertThat(service.expand(original, "attachment", "12", 5, false, true)).isSameAs(original);
        assertThat(service.candidateLimit(0)).isEqualTo(4);
    }
}
