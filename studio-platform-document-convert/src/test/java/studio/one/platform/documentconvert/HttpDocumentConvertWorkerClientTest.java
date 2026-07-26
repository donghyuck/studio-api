package studio.one.platform.documentconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;
import studio.one.platform.documentconvert.infrastructure.worker.HttpDocumentConvertWorkerClient;

class HttpDocumentConvertWorkerClientTest {

    @Test
    void submitSendsJsonRequestBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/pandoc/jobs", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            var client = new HttpDocumentConvertWorkerClient(
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                    objectMapper,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "test-token",
                    Duration.ofSeconds(5));
            DocumentConvertJob job = DocumentConvertJob.pending(
                    "job-1", "3", DocumentFormat.DOCX, DocumentFormat.MARKDOWN,
                    "{}", "test-user", Instant.now());

            client.submit(
                    job,
                    URI.create("http://server/source"),
                    URI.create("http://server/upload"),
                    "upload-token",
                    URI.create("http://server/callback"),
                    "4",
                    Map.of());

            assertFalse(requestBody.get().isBlank());
            JsonNode json = objectMapper.readTree(requestBody.get());
            assertEquals("job-1", json.path("jobId").asText());
            assertEquals("docx", json.path("sourceFormat").asText());
            assertEquals("markdown", json.path("targetFormat").asText());
            assertEquals("upload-token", json.path("uploadToken").asText());
        } finally {
            server.stop(0);
        }
    }
}
