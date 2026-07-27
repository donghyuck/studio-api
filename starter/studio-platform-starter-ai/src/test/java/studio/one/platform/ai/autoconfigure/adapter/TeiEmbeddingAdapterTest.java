package studio.one.platform.ai.autoconfigure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.embedding.EmbeddingRequest;

class TeiEmbeddingAdapterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsTeiEmbeddingResponseToEmbeddingVectors() throws IOException {
        server = startServer("[[0.1,0.2],[0.3,0.4]]", 200);
        TeiEmbeddingAdapter adapter = new TeiEmbeddingAdapter(baseUrl(), "nlpai-lab/KURE-v1");

        var response = adapter.embed(new EmbeddingRequest(
                List.of("첫 번째 문장", "두 번째 문장"),
                "kure",
                "nlpai-lab/KURE-v1",
                null,
                null));

        assertThat(response.vectors()).hasSize(2);
        assertThat(response.vectors().get(0).referenceId()).isEqualTo("첫 번째 문장");
        assertThat(response.vectors().get(0).values()).containsExactly(0.1, 0.2);
        assertThat(response.vectors().get(1).referenceId()).isEqualTo("두 번째 문장");
        assertThat(response.vectors().get(1).values()).containsExactly(0.3, 0.4);
    }

    @Test
    void rejectsDifferentRequestedModelWhenConfiguredModelExists() throws IOException {
        server = startServer("[[0.1]]", 200);
        TeiEmbeddingAdapter adapter = new TeiEmbeddingAdapter(baseUrl(), "nlpai-lab/KURE-v1");

        assertThatThrownBy(() -> adapter.embed(new EmbeddingRequest(
                List.of("text"),
                "kure",
                "other-model",
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match configured TEI embedding model");
    }

    private HttpServer startServer(String responseBody, int status) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/embed", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(new String(requestBody, StandardCharsets.UTF_8)).contains("\"inputs\"");

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
