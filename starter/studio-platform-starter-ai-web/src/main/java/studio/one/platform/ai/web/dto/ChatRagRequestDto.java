package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Chat 요청에 RAG 검색을 결합하기 위한 DTO.
 */
public record ChatRagRequestDto(
        @NotNull @Valid ChatRequestDto chat,
        String ragQuery,
        @Min(value = 1, message = "ragTopK must be at least 1")
        @Max(value = 100, message = "ragTopK must be at most 100")
        Integer ragTopK,
        String objectType,
        String objectId,
        @com.fasterxml.jackson.annotation.JsonAlias("embeddingModelId") String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 100, message = "topK must be at most 100")
        Integer topK,
        @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore,
        Boolean debug,
        String retrievalStrategy,
        @Valid ChatRagRetrievalOptionsDto retrievalOptions,
        String embeddingDeploymentId,
        String answerMode,
        String sourceScope,
        @Valid ExternalSourceOptionsDto externalSourceOptions,
        @Size(max = 10, message = "indexedWebSources must contain at most 10 sources")
        List<@Valid IndexedWebSourceRefDto> indexedWebSources,
        @Positive(message = "teamId must be positive") Long teamId,
        @Positive(message = "workspaceId must be positive") Long workspaceId
) {
    public ChatRagRequestDto {
        if (workspaceId != null && teamId == null) {
            throw new IllegalArgumentException("workspaceId requires teamId");
        }
        if (teamId != null && (hasText(objectType) || hasText(objectId))) {
            throw new IllegalArgumentException("Team RAG scope cannot be combined with objectType/objectId");
        }
        if (teamId != null && indexedWebSources != null && !indexedWebSources.isEmpty()) {
            throw new IllegalArgumentException("Team RAG scope cannot be combined with indexedWebSources");
        }
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer topK,
            Double minScore,
            Boolean debug,
            String retrievalStrategy,
            ChatRagRetrievalOptionsDto retrievalOptions,
            String embeddingDeploymentId,
            String answerMode,
            String sourceScope,
            ExternalSourceOptionsDto externalSourceOptions,
            List<IndexedWebSourceRefDto> indexedWebSources) {
        this(chat, ragQuery, ragTopK, objectType, objectId, embeddingProfileId, embeddingProvider,
                embeddingModel, topK, minScore, debug, retrievalStrategy, retrievalOptions,
                embeddingDeploymentId, answerMode, sourceScope, externalSourceOptions, indexedWebSources,
                null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer topK,
            Double minScore,
            Boolean debug,
            String retrievalStrategy,
            ChatRagRetrievalOptionsDto retrievalOptions,
            String embeddingDeploymentId,
            String answerMode) {
        this(
                chat,
                ragQuery,
                ragTopK,
                objectType,
                objectId,
                embeddingProfileId,
                embeddingProvider,
                embeddingModel,
                topK,
                minScore,
                debug,
                retrievalStrategy,
                retrievalOptions,
                embeddingDeploymentId,
                answerMode,
                null,
                null,
                null,
                null,
                null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer topK,
            Double minScore,
            Boolean debug,
            String retrievalStrategy,
            ChatRagRetrievalOptionsDto retrievalOptions,
            String embeddingDeploymentId) {
        this(
                chat,
                ragQuery,
                ragTopK,
                objectType,
                objectId,
                embeddingProfileId,
                embeddingProvider,
                embeddingModel,
                topK,
                minScore,
                debug,
                retrievalStrategy,
                retrievalOptions,
                embeddingDeploymentId,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            Boolean debug) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, debug, null, null, null, null,
                null, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer topK,
            Double minScore,
            Boolean debug) {
        this(chat, ragQuery, ragTopK, objectType, objectId, embeddingProfileId, embeddingProvider, embeddingModel,
                topK, minScore, debug, null, null, null, null, null, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer topK,
            Double minScore,
            Boolean debug,
            String retrievalStrategy,
            ChatRagRetrievalOptionsDto retrievalOptions) {
        this(chat, ragQuery, ragTopK, objectType, objectId, embeddingProfileId, embeddingProvider, embeddingModel,
                topK, minScore, debug, retrievalStrategy, retrievalOptions, null, null, null, null, null, null, null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
