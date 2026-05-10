package studio.one.platform.textract.autoconfigure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf.PyMuPdf4LlmResponse;

class PyMuPdf4LlmHttpClient implements PyMuPdf4LlmClient {

    private static final String CRLF = "\r\n";

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

        try {
            String boundary = boundary();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(request, boundary)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileParseException("PyMuPDF4LLM worker returned HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), PyMuPdf4LlmResponse.class);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileParseException("Interrupted while calling PyMuPDF4LLM worker", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new FileParseException("Failed to call PyMuPDF4LLM worker", ex);
        }
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
        options.put("preserveLayout", request.options().preserveLayout());
        options.put("tableExtractionRequired", request.options().tableExtractionRequired());
        options.put("filename", sanitizeFilename(request.filename()));
        options.put("contentType", sanitizeHeaderValue(request.contentType(), "application/pdf"));
        return objectMapper.writeValueAsString(options);
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
