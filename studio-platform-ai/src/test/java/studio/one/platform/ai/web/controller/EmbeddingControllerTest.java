package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.web.dto.EmbeddingRequestDto;
import studio.one.platform.ai.web.dto.EmbeddingResponseDto;
import studio.one.platform.web.dto.ApiResponse;

class EmbeddingControllerTest {

    @Test
    void delegatesEmbeddingRequestsToInjectedEmbeddingPort() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        EmbeddingController controller = new EmbeddingController(embeddingPort);

        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(
                List.of(new EmbeddingVector("first", List.of(1.0, 2.0)))));

        ResponseEntity<ApiResponse<EmbeddingResponseDto>> response =
                controller.embed(new EmbeddingRequestDto(List.of("first")));

        verify(embeddingPort).embed(any());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        EmbeddingResponseDto body = response.getBody().getData();
        assertThat(body.vectors()).hasSize(1);
        assertThat(body.vectors().get(0).referenceId()).isEqualTo("first");
        assertThat(body.vectors().get(0).values()).containsExactly(1.0, 2.0);
    }
}
