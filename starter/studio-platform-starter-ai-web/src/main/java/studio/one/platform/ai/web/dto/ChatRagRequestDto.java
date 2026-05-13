package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Chat 요청에 RAG 검색을 결합하기 위한 DTO.
 */
public class ChatRagRequestDto {

    @NotNull
    @Valid
    private final ChatRequestDto chat;

    private final String ragQuery;

    @Min(value = 1, message = "ragTopK must be at least 1")
    @Max(value = 100, message = "ragTopK must be at most 100")
    private final Integer ragTopK;

    private final String objectType;

    private final String objectId;

    private final String embeddingProfileId;

    private final String embeddingProvider;

    private final String embeddingModel;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 100, message = "topK must be at most 100")
    private final Integer topK;

    @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
    private final Double minScore;

    private final Boolean debug;

    @JsonCreator
    public ChatRagRequestDto(
            @JsonProperty("chat") ChatRequestDto chat,
            @JsonProperty("ragQuery") String ragQuery,
            @JsonProperty("ragTopK") Integer ragTopK,
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("embeddingProfileId") String embeddingProfileId,
            @JsonProperty("embeddingProvider") String embeddingProvider,
            @JsonProperty("embeddingModel") String embeddingModel,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("minScore") Double minScore,
            @JsonProperty("debug") Boolean debug
    ) {
        this.chat = chat;
        this.ragQuery = ragQuery;
        this.ragTopK = ragTopK;
        this.objectType = objectType;
        this.objectId = objectId;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.topK = topK;
        this.minScore = minScore;
        this.debug = debug;
    }

    public ChatRequestDto chat() {
        return chat;
    }

    public ChatRequestDto getChat() {
        return chat;
    }

    public String ragQuery() {
        return ragQuery;
    }

    public String getRagQuery() {
        return ragQuery;
    }

    public Integer ragTopK() {
        return ragTopK;
    }

    public Integer getRagTopK() {
        return ragTopK;
    }

    public String objectType() {
        return objectType;
    }

    public String getObjectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    public String embeddingProfileId() {
        return embeddingProfileId;
    }

    public String getEmbeddingProfileId() {
        return embeddingProfileId;
    }

    public String embeddingProvider() {
        return embeddingProvider;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer topK() {
        return topK;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double minScore() {
        return minScore;
    }

    public Double getMinScore() {
        return minScore;
    }

    public Boolean debug() {
        return debug;
    }

    public Boolean getDebug() {
        return debug;
    }

public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            Boolean debug) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, debug);
    }

}