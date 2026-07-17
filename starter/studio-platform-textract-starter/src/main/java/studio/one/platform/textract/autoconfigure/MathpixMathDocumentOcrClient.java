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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathDocumentOcrClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

class MathpixMathDocumentOcrClient implements MathDocumentOcrClient {

    private static final String CRLF = "\r\n";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final URI apiBaseUrl;
    private final Duration timeout;
    private final Duration pollInterval;
    private final int maxPollAttempts;
    private final String appId;
    private final String appKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    MathpixMathDocumentOcrClient(
            String apiBaseUrl,
            Duration timeout,
            Duration pollInterval,
            int maxPollAttempts,
            String appId,
            String appKey,
            ObjectMapper objectMapper) {
        this.apiBaseUrl = URI.create(trimTrailingSlash(apiBaseUrl == null ? "https://api.mathpix.com/v3" : apiBaseUrl));
        this.timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
        this.pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        this.maxPollAttempts = Math.max(1, maxPollAttempts);
        this.appId = appId == null ? "" : appId;
        this.appKey = appKey == null ? "" : appKey;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public boolean available() {
        return !appId.isBlank() && !appKey.isBlank();
    }

    @Override
    public String provider() {
        return "mathpix";
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        if (!available()) {
            throw new FileParseException("Mathpix math OCR credentials are not configured.");
        }
        long started = System.nanoTime();
        try {
            String pdfId = submit(request);
            Map<String, Object> status = poll(pdfId);
            String markdown = downloadMarkdown(pdfId);
            if (markdown.isBlank()) {
                markdown = firstText(status, "markdown", "md", "mmd", "text");
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "mathpix");
            metadata.put("extractionEngine", "mathpix");
            metadata.put("mathOcrProvider", "mathpix");
            metadata.put("mathOcrApplied", true);
            metadata.put("mathMarkdownApplied", true);
            metadata.put("mathMarkdownEngine", "mathpix");
            metadata.put("mathMarkdownQuality", "VALID");
            metadata.put("mathDocumentEngineRequired", false);
            metadata.put("mathpixPdfId", pdfId);
            metadata.put("elapsedMs", elapsedMs(started));
            metadata.put("pdfAnalysis", analysis == null ? Map.of() : analysis.metadata());
            List<ParsedBlock> blocks = blocks(markdown, defaultPage(request));
            if (blocks.size() == 1) {
                metadata.put("pageProvenanceStatus", "REVIEW_REQUIRED");
                metadata.put("markdownQualityIssues", List.of("PAGE_INFERRED"));
            } else {
                metadata.put("pageProvenanceStatus", "VALID");
            }
            return new ParsedFile(DocumentFormat.PDF, markdown, blocks, metadata, warnings(status),
                    List.of(), List.of(), List.of(), true, markdown, "markdown", List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileParseException("Interrupted while calling Mathpix math OCR", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new FileParseException("Failed to call Mathpix math OCR: " + ex.getMessage(), ex);
        }
    }

    private String submit(PdfExtractionRequest request) throws IOException, InterruptedException {
        String boundary = "----studio-mathpix-" + UUID.randomUUID();
        HttpRequest httpRequest = requestBuilder(apiBaseUrl.resolve(apiBaseUrl.getPath() + "/pdf"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(request, boundary)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FileParseException("Mathpix PDF submit returned HTTP " + response.statusCode()
                    + " body=" + abbreviate(response.body()));
        }
        Map<String, Object> body = objectMapper.readValue(response.body(), MAP_TYPE);
        String pdfId = firstText(body, "pdf_id", "pdfId", "id");
        if (pdfId.isBlank()) {
            throw new FileParseException("Mathpix PDF submit response did not include pdf_id.");
        }
        return pdfId;
    }

    private Map<String, Object> poll(String pdfId) throws IOException, InterruptedException {
        URI uri = apiBaseUrl.resolve(apiBaseUrl.getPath() + "/pdf/" + pdfId);
        Map<String, Object> last = Map.of();
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            HttpResponse<String> response = httpClient.send(requestBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileParseException("Mathpix PDF status returned HTTP " + response.statusCode()
                        + " body=" + abbreviate(response.body()));
            }
            last = objectMapper.readValue(response.body(), MAP_TYPE);
            String status = firstText(last, "status", "conversion_status");
            if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                return last;
            }
            if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                throw new FileParseException("Mathpix PDF conversion failed: " + abbreviate(String.valueOf(last)));
            }
            Thread.sleep(pollInterval.toMillis());
        }
        throw new FileParseException("Mathpix PDF conversion timed out: " + abbreviate(String.valueOf(last)));
    }

    private String downloadMarkdown(String pdfId) throws IOException, InterruptedException {
        URI uri = apiBaseUrl.resolve(apiBaseUrl.getPath() + "/pdf/" + pdfId + ".mmd");
        HttpResponse<String> response = httpClient.send(requestBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return "";
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FileParseException("Mathpix markdown download returned HTTP " + response.statusCode()
                    + " body=" + abbreviate(response.body()));
        }
        return response.body() == null ? "" : response.body();
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("app_id", appId)
                .header("app_key", appKey);
    }

    private byte[] multipartBody(PdfExtractionRequest request, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextPart(out, boundary, "options_json", "{\"conversion_formats\":{\"md\":true},\"math_inline_delimiters\":[\"$\",\"$\"],\"rm_spaces\":true}");
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename(request) + "\"" + CRLF)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/pdf" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(request.bytes());
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private List<ParsedBlock> blocks(String markdown, int defaultPage) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<ParsedBlock> blocks = new java.util.ArrayList<>();
        int currentPage = defaultPage;
        int order = 0;
        StringBuilder pageText = new StringBuilder();
        for (String line : markdown.lines().toList()) {
            Integer markerPage = pageMarker(line);
            if (markerPage != null) {
                order = flushBlock(blocks, pageText, currentPage, order);
                currentPage = markerPage;
                continue;
            }
            if (!line.isBlank()) {
                if (pageText.length() > 0) {
                    pageText.append('\n');
                }
                pageText.append(line);
            }
        }
        flushBlock(blocks, pageText, currentPage, order);
        return blocks.isEmpty()
                ? List.of(ParsedBlock.text("mathpix/block[0]", BlockType.DOCUMENT, markdown, defaultPage, 0,
                        Map.of("sourceRef", "page[" + defaultPage + "]/mathpix-block[0]", "page", defaultPage,
                                "pageProvenanceStatus", "PAGE_INFERRED")))
                : List.copyOf(blocks);
    }

    private int flushBlock(List<ParsedBlock> blocks, StringBuilder text, int page, int order) {
        if (text.isEmpty()) {
            return order;
        }
        String sourceRef = "page[" + page + "]/mathpix-block[" + order + "]";
        blocks.add(ParsedBlock.text("mathpix/block[" + order + "]", BlockType.DOCUMENT, text.toString(), page, order,
                Map.of("sourceRef", sourceRef, "page", page)));
        text.setLength(0);
        return order + 1;
    }

    private Integer pageMarker(String line) {
        if (line == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\s*:?\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int defaultPage(PdfExtractionRequest request) {
        if (request.options().pageFrom() != null) {
            return request.options().pageFrom();
        }
        if (request.options().pageTo() != null) {
            return request.options().pageTo();
        }
        return 1;
    }

    private List<ParseWarning> warnings(Map<String, Object> status) {
        String error = firstText(status, "error", "error_info");
        return error.isBlank() ? List.of()
                : List.of(ParseWarning.warning("MATHPIX_WARNING", error, "document", Map.of()));
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String filename(PdfExtractionRequest request) {
        String filename = request.filename() == null || request.filename().isBlank() ? "document.pdf" : request.filename();
        return filename.replace("\\", "_").replace("/", "_").replace("\"", "");
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String text = value.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private String trimTrailingSlash(String value) {
        String text = value == null || value.isBlank() ? "https://api.mathpix.com/v3" : value.trim();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }
}
