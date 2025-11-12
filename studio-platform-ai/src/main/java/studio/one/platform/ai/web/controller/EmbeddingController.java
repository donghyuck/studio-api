package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Objects;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.web.dto.EmbeddingRequestDto;
import studio.one.platform.ai.web.dto.EmbeddingResponseDto;
import studio.one.platform.ai.web.dto.EmbeddingVectorDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/ai/embedding")
@Validated
public class EmbeddingController {

    private final EmbeddingPort embeddingPort;

    public EmbeddingController(EmbeddingPort embeddingPort) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmbeddingResponseDto>> embed(@Valid @RequestBody EmbeddingRequestDto request) {
        EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(request.texts()));
        return ResponseEntity.ok(ApiResponse.ok(new EmbeddingResponseDto(toEmbeddingVectors(response))));
    }

    private List<EmbeddingVectorDto> toEmbeddingVectors(EmbeddingResponse response) {
        return response.vectors().stream()
                .map(vector -> new EmbeddingVectorDto(vector.referenceId(), List.copyOf(vector.values())))
                .toList();
    }
}
