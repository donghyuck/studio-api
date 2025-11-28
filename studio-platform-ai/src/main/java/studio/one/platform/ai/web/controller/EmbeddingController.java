package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Objects;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * REST API for generating embeddings with the configured model.
 * <p>Base path is configurable via {@code studio.ai.endpoints.base-path} (default {@code /api/ai}).
 * Endpoint: {@code POST {basePath}/embedding} returning {@link ApiResponse} containing
 * {@link EmbeddingResponseDto}.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/embedding")
@Validated
public class EmbeddingController {

    private final EmbeddingPort embeddingPort;

    public EmbeddingController(EmbeddingPort embeddingPort) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    /**
     * Handles vector embedding requests under {@code ${studio.ai.endpoints.base-path:/api/ai}/embedding}.
     * <p>Typical usage:
     * <pre>
     * POST /api/ai/embedding
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_embedding write)
     * {
     *   "texts": ["hello world", "another text"]
     * }
     *
     * 200 OK
     * {
     *   "data": {
     *     "vectors": [
     *       {"referenceId":"0","values":[0.1,0.2,...]}
     *     ]
     *   }
     * }
     * </pre>
     * Provide one or more text values and receive a list of embeddings in the same order.
     */
    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_embedding','write')")
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
