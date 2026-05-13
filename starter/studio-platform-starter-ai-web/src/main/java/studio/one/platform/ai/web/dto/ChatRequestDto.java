package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO for submitting chat requests.
 */
public class ChatRequestDto {

    private final String provider;

    private final String systemPrompt;

    @NotEmpty(message = "At least one chat message is required")
    @Valid
    private final List<ChatMessageDto> messages;

    private final String model;

    private final Double temperature;

    private final Double topP;

    private final Integer topK;

    private final Integer maxOutputTokens;

    private final List<String> stopSequences;

    private final ChatMemoryOptionsDto memory;

    @JsonCreator
    public ChatRequestDto(
            @JsonProperty("provider") String provider,
            @JsonProperty("systemPrompt") String systemPrompt,
            @JsonProperty("messages") List<ChatMessageDto> messages,
            @JsonProperty("model") String model,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("topP") Double topP,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("maxOutputTokens") Integer maxOutputTokens,
            @JsonProperty("stopSequences") List<String> stopSequences,
            @JsonProperty("memory") ChatMemoryOptionsDto memory
    ) {
        this.provider = provider;
        this.systemPrompt = systemPrompt;
        this.messages = messages;
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxOutputTokens = maxOutputTokens;
        this.stopSequences = stopSequences;
        this.memory = memory;
    }

    public String provider() {
        return provider;
    }

    public String getProvider() {
        return provider;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<ChatMessageDto> messages() {
        return messages;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Integer getTopK() {
        return topK;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public List<String> stopSequences() {
        return stopSequences;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public ChatMemoryOptionsDto memory() {
        return memory;
    }

    public ChatMemoryOptionsDto getMemory() {
        return memory;
    }

public ChatRequestDto(
            String provider,
            String systemPrompt,
            List<ChatMessageDto> messages,
            String model,
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxOutputTokens,
            List<String> stopSequences) {
        this(provider, systemPrompt, messages, model, temperature, topP, topK, maxOutputTokens, stopSequences, null);
    }

}