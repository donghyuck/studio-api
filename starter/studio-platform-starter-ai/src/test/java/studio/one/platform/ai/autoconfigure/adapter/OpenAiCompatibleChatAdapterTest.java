package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponseMetadata;
import studio.one.platform.ai.core.chat.TokenUsage;

class OpenAiCompatibleChatAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsOpenAiCompatibleChatBodyAndMapsResponse() throws Exception {
        AtomicReference<JsonNode> capturedBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = startServer(capturedBody, authorization, """
                {
                  "id": "chatcmpl-test",
                  "model": "gemma-3-4b",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "임베딩은 의미 기반 검색을 위한 벡터를 만듭니다."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 20,
                    "total_tokens": 30,
                    "prompt_tokens_details": {
                      "cached_tokens": 4,
                      "cache_write_tokens": 2
                    }
                  }
                }
                """, 200);
        OpenAiCompatibleChatAdapter adapter = new OpenAiCompatibleChatAdapter(
                baseUrl(), "local-dev", "local-gemma", "gemma-3-4b", null, objectMapper);

        var response = adapter.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("한국어로 답하세요."),
                        ChatMessage.user("임베딩 역할은?")))
                .temperature(0.2)
                .topP(0.9)
                .maxOutputTokens(80)
                .build());

        assertThat(capturedBody.get().path("model").asText()).isEqualTo("gemma-3-4b");
        assertThat(capturedBody.get().path("messages")).hasSize(2);
        assertThat(capturedBody.get().path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(capturedBody.get().path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(capturedBody.get().path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(capturedBody.get().path("top_p").asDouble()).isEqualTo(0.9);
        assertThat(capturedBody.get().path("max_tokens").asInt()).isEqualTo(80);
        assertThat(authorization.get()).isEqualTo("Bearer local-dev");

        assertThat(response.model()).isEqualTo("gemma-3-4b");
        assertThat(response.messages().get(0).content()).isEqualTo("임베딩은 의미 기반 검색을 위한 벡터를 만듭니다.");
        assertThat(response.metadata()).containsEntry(ChatResponseMetadata.KEY_PROVIDER, "local-gemma");
        assertThat(response.metadata()).containsEntry(ChatResponseMetadata.KEY_RESOLVED_MODEL, "gemma-3-4b");
        assertThat(response.metadata()).containsEntry("finishReason", "stop");
        assertThat((Object) response.metadata().get(ChatResponseMetadata.KEY_TOKEN_USAGE))
                .isEqualTo(java.util.Map.of(
                        TokenUsage.KEY_INPUT_TOKENS, 10,
                        TokenUsage.KEY_OUTPUT_TOKENS, 20,
                        TokenUsage.KEY_TOTAL_TOKENS, 30));
        assertThat(response.typedMetadata().promptCacheUsage()).isNotNull();
        assertThat(response.typedMetadata().promptCacheUsage().uncachedInputTokens()).isEqualTo(4);
        assertThat(response.typedMetadata().promptCacheUsage().cacheReadInputTokens()).isEqualTo(4);
        assertThat(response.typedMetadata().promptCacheUsage().cacheWriteInputTokens()).isEqualTo(2);
        assertThat(capturedBody.get().has("prompt_cache_key")).isFalse();
        assertThat(capturedBody.get().has("prompt_cache_options")).isFalse();
    }

    @Test
    void failsWithHttpStatusAndBodyPreview() throws Exception {
        server = startServer(new AtomicReference<>(), new AtomicReference<>(), "{\"error\":\"bad request\"}", 400);
        OpenAiCompatibleChatAdapter adapter = new OpenAiCompatibleChatAdapter(
                baseUrl(), null, "local-gemma", "gemma-3-4b", null, objectMapper);

        assertThatThrownBy(() -> adapter.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user("hello")))
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenAI-compatible chat request failed with status 400")
                .hasMessageContaining("bad request");
    }

    private HttpServer startServer(AtomicReference<JsonNode> capturedBody, AtomicReference<String> authorization,
            String responseBody, int status) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/chat/completions", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getProtocol()).isEqualTo("HTTP/1.1");
            assertThat(exchange.getRequestHeaders().getFirst("Accept")).isEqualTo("application/json");
            capturedBody.set(objectMapper.readTree(requestBody));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));

            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();
        return httpServer;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
