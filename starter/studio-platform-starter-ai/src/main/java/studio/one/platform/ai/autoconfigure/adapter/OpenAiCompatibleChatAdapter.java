package studio.one.platform.ai.autoconfigure.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.PromptCacheUsage;
import studio.one.platform.ai.core.chat.TokenUsage;

/**
 * Minimal OpenAI-compatible chat adapter for local vLLM/llama.cpp style servers.
 */
public class OpenAiCompatibleChatAdapter implements ChatPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI chatCompletionsUri;
    private final String apiKey;
    private final String provider;
    private final String configuredModel;
    private final Duration requestTimeout;

    public OpenAiCompatibleChatAdapter(String baseUrl, String apiKey, String provider, String configuredModel,
            Duration requestTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
                new ObjectMapper(),
                baseUrl,
                apiKey,
                provider,
                configuredModel,
                requestTimeout);
    }

    OpenAiCompatibleChatAdapter(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String apiKey,
            String provider, String configuredModel, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.chatCompletionsUri = URI.create(normalizeChatCompletionsUrl(baseUrl));
        this.apiKey = normalize(apiKey);
        this.provider = firstNonBlank(provider, "OPENAI");
        this.configuredModel = requireText(configuredModel, "OpenAI-compatible chat model must not be blank");
        this.requestTimeout = sanitizeTimeout(requestTimeout);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            String model = firstNonBlank(request.model(), configuredModel);
            String body = objectMapper.writeValueAsString(requestBody(request, model));
            HttpRequest.Builder builder = HttpRequest.newBuilder(chatCompletionsUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (apiKey != null) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            long startedAt = System.nanoTime();
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMillis(startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI-compatible chat request failed with status "
                        + response.statusCode() + ": " + abbreviate(response.body()));
            }
            return parseResponse(response.body(), model, latencyMs);
        } catch (HttpConnectTimeoutException e) {
            throw new IllegalStateException("Timed out connecting to OpenAI-compatible chat server after "
                    + requestTimeout, e);
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("Timed out waiting for OpenAI-compatible chat server after "
                    + requestTimeout, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call OpenAI-compatible chat server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling OpenAI-compatible chat server", e);
        }
    }

    private Map<String, Object> requestBody(ChatRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages(request.messages()));
        putIfNotNull(body, "temperature", request.temperature());
        putIfNotNull(body, "top_p", request.topP());
        putIfNotNull(body, "max_tokens", request.maxOutputTokens());
        if (request.stopSequences() != null && !request.stopSequences().isEmpty()) {
            body.put("stop", request.stopSequences());
        }
        return body;
    }

    private List<Map<String, String>> messages(List<ChatMessage> messages) {
        List<Map<String, String>> values = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("role", role(message));
            value.put("content", message.content());
            values.add(value);
        }
        return values;
    }

    private String role(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private ChatResponse parseResponse(String body, String requestedModel, long latencyMs) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            content = choice.path("text").asText("");
        }
        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI-compatible chat server returned an empty response");
        }

        String model = firstNonBlank(root.path("model").asText(null), requestedModel, configuredModel);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ChatResponseMetadata.KEY_PROVIDER, provider);
        metadata.put(ChatResponseMetadata.KEY_RESOLVED_MODEL, model);
        metadata.put(ChatResponseMetadata.KEY_LATENCY_MS, latencyMs);
        putIfNotBlank(metadata, "responseId", root.path("id").asText(null));
        putIfNotBlank(metadata, "modelName", root.path("model").asText(null));
        putIfNotBlank(metadata, "finishReason", choice.path("finish_reason").asText(null));
        Map<String, Integer> tokenUsage = tokenUsage(root.path("usage"));
        if (!tokenUsage.isEmpty()) {
            metadata.put(ChatResponseMetadata.KEY_TOKEN_USAGE, tokenUsage);
        }
        PromptCacheUsage promptCacheUsage = promptCacheUsage(root.path("usage"), tokenUsage);
        if (promptCacheUsage != null && promptCacheUsage.reported()) {
            metadata.put(ChatResponseMetadata.KEY_PROMPT_CACHE_USAGE, promptCacheUsage.toMap());
        }

        return new ChatResponse(List.of(ChatMessage.assistant(content)), model, metadata);
    }

    private Map<String, Integer> tokenUsage(JsonNode usage) {
        Map<String, Integer> values = new LinkedHashMap<>();
        putIfPresent(values, TokenUsage.KEY_INPUT_TOKENS, usage.path("prompt_tokens"));
        putIfPresent(values, TokenUsage.KEY_OUTPUT_TOKENS, usage.path("completion_tokens"));
        putIfPresent(values, TokenUsage.KEY_TOTAL_TOKENS, usage.path("total_tokens"));
        return values;
    }

    private PromptCacheUsage promptCacheUsage(JsonNode usage, Map<String, Integer> tokenUsage) {
        JsonNode details = usage.path("prompt_tokens_details");
        Integer cachedTokens = integer(details.path("cached_tokens"));
        if (cachedTokens == null) {
            cachedTokens = integer(usage.path("cache_read_input_tokens"));
        }
        Integer writeTokens = integer(details.path("cache_write_tokens"));
        if (writeTokens == null) {
            writeTokens = integer(usage.path("cache_write_tokens"));
        }
        if (writeTokens == null) {
            writeTokens = integer(usage.path("cache_creation_input_tokens"));
        }
        if (cachedTokens == null && writeTokens == null) {
            return null;
        }
        Integer inputTokens = tokenUsage.get(TokenUsage.KEY_INPUT_TOKENS);
        if (inputTokens != null && cachedTokens != null && writeTokens != null) {
            return PromptCacheUsage.complete(inputTokens, cachedTokens, writeTokens);
        }
        return PromptCacheUsage.partial(cachedTokens, writeTokens);
    }

    private static Integer integer(JsonNode node) {
        return node != null && node.canConvertToInt() ? Math.max(0, node.asInt()) : null;
    }

    private static void putIfPresent(Map<String, Integer> values, String key, JsonNode node) {
        if (node != null && node.canConvertToInt()) {
            values.put(key, node.asInt());
        }
    }

    private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static void putIfNotBlank(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private static String normalizeChatCompletionsUrl(String value) {
        String baseUrl = requireText(value, "OpenAI-compatible baseUrl must not be blank");
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/v1/chat/completions") || baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }

    private static Duration sanitizeTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofMinutes(2);
        }
        return timeout;
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }
}
