package studio.one.platform.documentconvert.infrastructure.worker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertWorkerClient;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public class HttpDocumentConvertWorkerClient implements DocumentConvertWorkerClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUrl;
    private final String internalToken;
    private final Duration requestTimeout;

    public HttpDocumentConvertWorkerClient(HttpClient httpClient, ObjectMapper objectMapper, URI baseUrl,
            String internalToken, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void submit(DocumentConvertJob job, URI sourceUrl, URI uploadUrl, String uploadToken,
            URI callbackUrl, String resultFileId, Map<String, Object> options) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jobId", job.jobId());
            body.put("sourceFormat", job.sourceFormat().name().toLowerCase());
            body.put("targetFormat", job.targetFormat().name().toLowerCase());
            body.put("targetContentType", contentType(job));
            body.put("sourceUrl", sourceUrl.toString());
            body.put("uploadUrl", uploadUrl.toString());
            body.put("uploadToken", uploadToken);
            body.put("callbackUrl", callbackUrl.toString());
            body.put("resultFileId", resultFileId);
            body.put("options", options);
            byte[] payload = objectMapper.writeValueAsBytes(body);
            HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/internal/pandoc/jobs"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json; charset=" + StandardCharsets.UTF_8.name())
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Pandoc worker rejected job with status " + response.statusCode()
                        + ": " + sanitizeResponse(response.body()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pandoc worker request interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Pandoc worker unavailable", ex);
        }
    }

    private String sanitizeResponse(String body) {
        if (body == null || body.isBlank()) {
            return "empty response";
        }
        String sanitized = body
                .replaceAll("https?://[^\\s\\\"']+", "[url]")
                .replaceAll("(?i)(token|secret)[^,}\\]]*", "$1=[redacted]");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private String contentType(DocumentConvertJob job) {
        return switch (job.targetFormat()) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case HTML -> "text/html";
            case MARKDOWN -> "text/markdown";
            case TEXT -> "text/plain";
        };
    }
}
