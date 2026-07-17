package studio.one.platform.textract.autoconfigure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmResponse;

class PyMuPdf4LlmHttpClient implements PyMuPdf4LlmClient {

    private static final String CRLF = "\r\n";
    private static final int RESPONSE_SNIPPET_LIMIT = 512;

    private final URI endpoint;
    private final Duration timeout;
    private final int maxFileSizeBytes;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    PyMuPdf4LlmHttpClient(
            String endpoint,
            Duration timeout,
            int maxFileSizeBytes,
            ObjectMapper objectMapper) {
        this.endpoint = URI.create(endpoint);
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public PyMuPdf4LlmResponse extract(PdfExtractionRequest request) throws FileParseException {
        if (request.bytes().length > maxFileSizeBytes) {
            throw new FileParseException("PDF exceeds PyMuPDF4LLM worker size limit: " + request.filename());
        }

        long startedNanos = System.nanoTime();
        try {
            String boundary = boundary();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(request, boundary)))
                    .build();
            HttpResponse<String> response = sendWithHardTimeout(httpRequest, startedNanos);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileParseException("PyMuPDF4LLM worker returned HTTP " + response.statusCode()
                        + " after " + elapsedMillis(startedNanos) + "ms"
                        + " body=" + abbreviate(response.body()));
            }
            try {
                return objectMapper.readValue(response.body(), PyMuPdf4LlmResponse.class);
            } catch (JsonProcessingException ex) {
                throw new FileParseException("Failed to parse PyMuPDF4LLM worker response after "
                        + elapsedMillis(startedNanos) + "ms"
                        + " bodyLength=" + bodyLength(response.body())
                        + " body=" + abbreviate(response.body()), ex);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileParseException("Interrupted while calling PyMuPDF4LLM worker after "
                    + elapsedMillis(startedNanos) + "ms", ex);
        } catch (HttpTimeoutException ex) {
            throw new FileParseException("Timed out calling PyMuPDF4LLM worker after "
                    + elapsedMillis(startedNanos) + "ms"
                    + " configuredTimeout=" + timeout, ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new FileParseException("Failed to call PyMuPDF4LLM worker after "
                    + elapsedMillis(startedNanos) + "ms"
                    + " cause=" + ex.getClass().getSimpleName()
                    + " message=" + abbreviate(ex.getMessage()), ex);
        }
    }

    private HttpResponse<String> sendWithHardTimeout(HttpRequest httpRequest, long startedNanos)
            throws IOException, InterruptedException, FileParseException {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                httpRequest,
                HttpResponse.BodyHandlers.ofString());
        try {
            return future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new FileParseException("Timed out calling PyMuPDF4LLM worker after "
                    + elapsedMillis(startedNanos) + "ms"
                    + " configuredTimeout=" + timeout, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof HttpTimeoutException timeoutException) {
                throw new FileParseException("Timed out calling PyMuPDF4LLM worker after "
                        + elapsedMillis(startedNanos) + "ms"
                        + " configuredTimeout=" + timeout, timeoutException);
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new FileParseException("Failed to call PyMuPDF4LLM worker after "
                    + elapsedMillis(startedNanos) + "ms"
                    + " cause=" + cause.getClass().getSimpleName()
                    + " message=" + abbreviate(cause.getMessage()), cause);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private int bodyLength(String body) {
        return body == null ? 0 : body.length();
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (sanitized.length() <= RESPONSE_SNIPPET_LIMIT) {
            return sanitized;
        }
        return sanitized.substring(0, RESPONSE_SNIPPET_LIMIT) + "...";
    }

    private String boundary() {
        return "----studio-textract-" + UUID.randomUUID();
    }

    private byte[] multipartBody(PdfExtractionRequest request, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextPart(out, boundary, "options", optionsJson(request));
        writeFilePart(out, boundary, request);
        out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/json; charset=utf-8" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(ByteArrayOutputStream out, String boundary, PdfExtractionRequest request) throws IOException {
        String filename = request.filename() == null || request.filename().isBlank() ? "document.pdf" : request.filename();
        String contentType = sanitizeHeaderValue(request.contentType(), "application/pdf");
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + sanitizeFilename(filename) + "\"" + CRLF)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(request.bytes());
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private String optionsJson(PdfExtractionRequest request) throws IOException {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("ocrRequired", request.options().ocrRequired());
        options.put("ocrMode", request.options().ocrMode());
        putIfPresent(options, "ocrLanguage", request.options().ocrLanguage());
        options.put("preserveLayout", request.options().preserveLayout());
        options.put("tableExtractionRequired", request.options().tableExtractionRequired());
        options.put("includeImages", request.options().includeImages());
        putIfPresent(options, "pageFrom", request.options().pageFrom());
        putIfPresent(options, "pageTo", request.options().pageTo());
        putIfPresent(options, "maxPages", request.options().maxPages());
        options.put("filename", sanitizeFilename(request.filename()));
        options.put("contentType", sanitizeHeaderValue(request.contentType(), "application/pdf"));
        return objectMapper.writeValueAsString(options);
    }

    private void putIfPresent(Map<String, Object> options, String key, Object value) {
        if (value != null) {
            options.put(key, value);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document.pdf";
        }
        String sanitized = firstHeaderLine(filename)
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "")
                .trim();
        return sanitized.isBlank() ? "document.pdf" : sanitized;
    }

    private String sanitizeHeaderValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = firstHeaderLine(value).trim();
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String firstHeaderLine(String value) {
        int lineBreak = -1;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\r' || current == '\n') {
                lineBreak = index;
                break;
            }
        }
        return lineBreak < 0 ? value : value.substring(0, lineBreak);
    }
}
