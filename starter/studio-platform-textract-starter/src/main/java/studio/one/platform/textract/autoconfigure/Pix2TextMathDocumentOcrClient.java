package studio.one.platform.textract.autoconfigure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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

class Pix2TextMathDocumentOcrClient implements MathDocumentOcrClient {

    private static final String CRLF = "\r\n";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final URI endpoint;
    private final Duration timeout;
    private final int maxFileSizeBytes;
    private final String language;
    private final boolean pageByPage;
    private final int batchSize;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    Pix2TextMathDocumentOcrClient(
            String endpoint,
            Duration timeout,
            int maxFileSizeBytes,
            String language,
            boolean pageByPage,
            int batchSize,
            ObjectMapper objectMapper) {
        this.endpoint = URI.create(endpoint);
        this.timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.language = language == null || language.isBlank() ? "ko,en" : language;
        this.pageByPage = pageByPage;
        this.batchSize = Math.max(1, batchSize);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String provider() {
        return "pix2text";
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        if (request.bytes().length > maxFileSizeBytes) {
            throw new FileParseException("PDF exceeds Pix2Text math OCR size limit: " + request.filename());
        }
        if (shouldExtractInBatches(request, analysis)) {
            return extractInBatches(request, analysis);
        }
        return extractSingle(request, analysis);
    }

    private ParsedFile extractSingle(PdfExtractionRequest request, PdfDocumentAnalysis analysis) throws FileParseException {
        long started = System.nanoTime();
        try {
            String boundary = "----studio-math-ocr-" + UUID.randomUUID();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(request, boundary)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileParseException("Pix2Text math OCR returned HTTP " + response.statusCode()
                        + " body=" + abbreviate(response.body()));
            }
            return parsedFile(request, analysis, response.body(), elapsedMs(started));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileParseException("Interrupted while calling Pix2Text math OCR", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new FileParseException("Failed to call Pix2Text math OCR: " + ex.getMessage(), ex);
        }
    }

    private boolean shouldExtractInBatches(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        int pageCount = pageCount(request, analysis);
        return pageByPage
                && batchSize > 0
                && pageCount > batchSize
                && !request.options().rangeRequested();
    }

    private int pageCount(PdfExtractionRequest request, PdfDocumentAnalysis analysis) {
        if (request.options().pageCount() != null && request.options().pageCount() > 0) {
            return request.options().pageCount();
        }
        return analysis == null ? 0 : Math.max(0, analysis.pageCount());
    }

    private ParsedFile extractInBatches(PdfExtractionRequest request, PdfDocumentAnalysis analysis)
            throws FileParseException {
        long started = System.nanoTime();
        int pageCount = pageCount(request, analysis);
        List<ParsedFile> parts = new ArrayList<>();
        for (int pageFrom = 1; pageFrom <= pageCount; pageFrom += batchSize) {
            int pageTo = Math.min(pageCount, pageFrom + batchSize - 1);
            PdfExtractionRequest partRequest = new PdfExtractionRequest(
                    request.bytes(),
                    request.contentType(),
                    request.filename(),
                    request.options().forPageRange(pageFrom, pageTo));
            parts.add(extractSingle(partRequest, analysis));
        }
        return mergeBatches(request, analysis, parts, elapsedMs(started), pageCount);
    }

    private ParsedFile mergeBatches(
            PdfExtractionRequest request,
            PdfDocumentAnalysis analysis,
            List<ParsedFile> parts,
            long elapsedMs,
            int pageCount) {
        StringBuilder markdown = new StringBuilder();
        StringBuilder plainText = new StringBuilder();
        List<ParsedBlock> blocks = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();
        List<Map<String, Object>> partSummaries = new ArrayList<>();
        for (ParsedFile part : parts) {
            if (markdown.length() > 0) {
                markdown.append("\n\n");
            }
            markdown.append(part.markdown());
            if (plainText.length() > 0) {
                plainText.append("\n\n");
            }
            plainText.append(part.plainText());
            blocks.addAll(part.blocks());
            warnings.addAll(part.warnings());
            partSummaries.add(partSummary(part));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pix2text");
        metadata.put("extractionEngine", "pix2text");
        metadata.put("mathOcrProvider", "pix2text");
        metadata.put("mathOcrApplied", true);
        metadata.put("mathMarkdownApplied", true);
        metadata.put("mathMarkdownEngine", "pix2text");
        metadata.put("mathMarkdownQuality", "VALID");
        metadata.put("mathDocumentEngineRequired", false);
        metadata.put("elapsedMs", elapsedMs);
        metadata.put("filename", request.filename());
        metadata.put("contentType", request.contentType());
        metadata.put("pdfAnalysis", analysis == null ? Map.of() : analysis.metadata());
        metadata.put("pageCount", pageCount);
        metadata.put("pageByPage", true);
        metadata.put("pageBatchSize", batchSize);
        metadata.put("pageBatchCount", parts.size());
        metadata.put("pix2textBatched", true);
        metadata.put("pix2textParts", partSummaries);
        return new ParsedFile(DocumentFormat.PDF, plainText.toString(), blocks, metadata, warnings,
                List.of(), List.of(), List.of(), true, markdown.toString(), "markdown", List.of());
    }

    private Map<String, Object> partSummary(ParsedFile part) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pageFrom", part.metadata().get("pageFrom"));
        summary.put("pageTo", part.metadata().get("pageTo"));
        summary.put("textLength", part.plainText().length());
        summary.put("blockCount", part.blocks().size());
        summary.put("elapsedMs", part.metadata().get("elapsedMs"));
        return summary;
    }

    private byte[] multipartBody(PdfExtractionRequest request, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTextPart(out, boundary, "options", optionsJson(request));
        writeFilePart(out, boundary, request);
        out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String optionsJson(PdfExtractionRequest request) throws IOException {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("language", language);
        options.put("filename", filename(request));
        options.put("contentType", contentType(request));
        options.put("format", "markdown");
        options.put("source", "studio-textract");
        options.put("pageByPage", pageByPage);
        putIfPresent(options, "pageFrom", request.options().pageFrom());
        putIfPresent(options, "pageTo", request.options().pageTo());
        putIfPresent(options, "maxPages", request.options().maxPages());
        return objectMapper.writeValueAsString(options);
    }

    private void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/json; charset=utf-8" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(ByteArrayOutputStream out, String boundary, PdfExtractionRequest request) throws IOException {
        out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename(request) + "\"" + CRLF)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType(request) + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        out.write(request.bytes());
        out.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private ParsedFile parsedFile(
            PdfExtractionRequest request,
            PdfDocumentAnalysis analysis,
            String body,
            long elapsedMs) throws IOException {
        String contentType = "";
        Map<String, Object> response;
        if (body != null && body.stripLeading().startsWith("{")) {
            response = objectMapper.readValue(body, MAP_TYPE);
        } else {
            response = Map.of("markdown", body == null ? "" : body);
            contentType = "markdown";
        }
        String markdown = firstText(response, "markdown", "md", "mmd", "text");
        String plainText = firstText(response, "plainText", "text", "markdown");
        if (plainText.isBlank()) {
            plainText = markdown;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pix2text");
        metadata.put("extractionEngine", "pix2text");
        metadata.put("mathOcrProvider", "pix2text");
        metadata.put("mathOcrApplied", true);
        metadata.put("mathMarkdownApplied", true);
        metadata.put("mathMarkdownEngine", "pix2text");
        metadata.put("mathMarkdownQuality", "VALID");
        metadata.put("mathDocumentEngineRequired", false);
        metadata.put("elapsedMs", elapsedMs);
        metadata.put("filename", request.filename());
        metadata.put("contentType", request.contentType());
        metadata.put("pdfAnalysis", analysis == null ? Map.of() : analysis.metadata());
        Object responseMetadata = response.get("metadata");
        if (responseMetadata instanceof Map<?, ?> map) {
            map.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        int defaultPage = defaultPage(request, response);
        List<ParsedBlock> blocks = blocks(response.get("blocks"), markdown, defaultPage);
        List<ParseWarning> warnings = warnings(response.get("warnings"));
        return new ParsedFile(DocumentFormat.PDF, plainText, blocks, metadata, warnings,
                List.of(), List.of(), List.of(), true, markdown, contentType.isBlank() ? "markdown" : contentType, List.of());
    }

    private List<ParsedBlock> blocks(Object value, String markdown, int defaultPage) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return markdown == null || markdown.isBlank()
                    ? List.of()
                    : List.of(ParsedBlock.text("pix2text/block[0]", BlockType.DOCUMENT, markdown, defaultPage, 0,
                            Map.of("sourceRef", "page[" + defaultPage + "]/pix2text-block[0]", "page", defaultPage)));
        }
        List<ParsedBlock> blocks = new ArrayList<>();
        int order = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String text = firstText(map, "text", "markdown", "latex");
                if (!text.isBlank()) {
                    Integer page = firstInteger(map.get("page"), map.get("pageNo"), map.get("pageNumber"),
                            pageFromSourceRef(text(map.get("sourceRef"))), defaultPage);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("sourceRef", text(map.get("sourceRef"), "page[" + page + "]/pix2text-block[" + order + "]"));
                    metadata.put("page", page);
                    metadata.put("order", order);
                    putIfPresent(metadata, "bbox", firstPresent(map.get("bbox"), map.get("boundingBox")));
                    blocks.add(ParsedBlock.text("pix2text/block[" + order + "]",
                            blockType(text(map.get("type"))), text, page, order, metadata));
                    order++;
                }
            }
        }
        return List.copyOf(blocks);
    }

    private int defaultPage(PdfExtractionRequest request, Map<String, Object> response) {
        Object metadata = response.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            Integer page = firstInteger(map.get("pageFrom"), map.get("page"), map.get("pageNumber"));
            if (page != null) {
                return page;
            }
        }
        Integer page = firstInteger(request.options().pageFrom(), request.options().pageTo());
        return page == null ? 1 : page;
    }

    private List<ParseWarning> warnings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> ParseWarning.warning("PIX2TEXT_WARNING", String.valueOf(item), "document", Map.of()))
                .toList();
    }

    private BlockType blockType(String type) {
        if (type == null || type.isBlank()) {
            return BlockType.PARAGRAPH;
        }
        try {
            return BlockType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BlockType.PARAGRAPH;
        }
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String text = text(map.get(key));
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private Object firstPresent(Object first, Object second) {
        return first == null ? second : first;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer firstInteger(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Integer integer = integer(value);
            if (integer != null) {
                return integer;
            }
        }
        return null;
    }

    private Integer pageFromSourceRef(String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\[(\\d+)]").matcher(sourceRef);
        if (!matcher.find()) {
            return null;
        }
        return integer(matcher.group(1));
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String filename(PdfExtractionRequest request) {
        String filename = request.filename() == null || request.filename().isBlank() ? "document.pdf" : request.filename();
        return filename.replace("\\", "_").replace("/", "_").replace("\"", "");
    }

    private String contentType(PdfExtractionRequest request) {
        return request.contentType() == null || request.contentType().isBlank()
                ? "application/pdf"
                : request.contentType().replace("\r", "").replace("\n", "");
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
}
