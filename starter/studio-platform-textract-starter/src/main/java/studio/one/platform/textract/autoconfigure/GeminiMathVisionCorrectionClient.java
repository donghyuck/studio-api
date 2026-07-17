package studio.one.platform.textract.autoconfigure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.MathVisionCorrectionClient;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

class GeminiMathVisionCorrectionClient implements MathVisionCorrectionClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxFileSizeBytes;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    GeminiMathVisionCorrectionClient(
            String baseUrl,
            String apiKey,
            String model,
            Duration timeout,
            int maxFileSizeBytes,
            ObjectMapper objectMapper) {
        String effectiveModel = text(model, "gemini-2.5-flash");
        String root = text(baseUrl, "https://generativelanguage.googleapis.com/v1beta")
                .replaceAll("/+$", "");
        this.endpoint = URI.create(root + "/models/" + effectiveModel + ":generateContent");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = effectiveModel;
        this.timeout = timeout == null ? Duration.ofMinutes(2) : timeout;
        this.maxFileSizeBytes = Math.max(1, maxFileSizeBytes);
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public boolean available() {
        return !apiKey.isBlank();
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public ParsedFile correct(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages)
            throws FileParseException {
        if (!available()) {
            throw new FileParseException("Gemini math vision correction api key is not configured.");
        }
        if (request.bytes().length > maxFileSizeBytes) {
            throw new FileParseException("Gemini math vision correction input exceeds max file size.");
        }
        long started = System.nanoTime();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(request, analysis, pages)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileParseException("Gemini math vision correction returned HTTP " + response.statusCode()
                        + " body=" + abbreviate(response.body()));
            }
            return parsedFile(response.body(), pages, elapsedMs(started));
        } catch (IOException ex) {
            throw new FileParseException("Failed to call Gemini math vision correction: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileParseException("Interrupted while calling Gemini math vision correction", ex);
        }
    }

    private String requestBody(PdfExtractionRequest request, PdfDocumentAnalysis analysis, List<Integer> pages)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(Map.of("parts", List.of(
                Map.of("text", prompt(analysis, pages)),
                Map.of("inline_data", Map.of(
                        "mime_type", request.contentType() == null ? "application/pdf" : request.contentType(),
                        "data", Base64.getEncoder().encodeToString(request.bytes())))))));
        payload.put("generationConfig", Map.of(
                "temperature", 0,
                "responseMimeType", "application/json"));
        return objectMapper.writeValueAsString(payload);
    }

    private String prompt(PdfDocumentAnalysis analysis, List<Integer> pages) {
        return """
                You are correcting OCR for a Korean high-school math textbook PDF.
                Extract only reliable mathematical expressions from these target pages: %s.
                Return strict JSON:
                {"formulas":[{"page":1,"latex":"$x^{2}+1$","confidence":0.0}]}
                Rules:
                - Korean prose is not needed.
                - Do not include icons, page decorations, or short Latin noise such as OO, SS, SAS, Sis, eT, loin.
                - Use Markdown LaTeX delimiters.
                - If uncertain, omit the formula.
                PDF analysis: %s
                """.formatted(pages == null ? List.of() : pages, analysis == null ? Map.of() : analysis.metadata());
    }

    private ParsedFile parsedFile(String body, List<Integer> pages, long elapsedMs) throws IOException {
        String text = candidateText(body, pages);
        List<ParsedBlock> blocks = blocks(text, pages);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "gemini");
        metadata.put("extractionEngine", "gemini");
        metadata.put("mathVisionProvider", "gemini");
        metadata.put("mathVisionCorrectionApplied", true);
        metadata.put("mathVisionModel", model);
        metadata.put("mathVisionFormulaCount", blocks.size());
        metadata.put("elapsedMs", elapsedMs);
        return new ParsedFile(DocumentFormat.PDF, text, blocks, metadata,
                blocks.isEmpty()
                        ? List.of(ParseWarning.warning("MATH_VISION_EMPTY_RESULT",
                                "Gemini math vision correction returned no accepted formulas.",
                                "document",
                                Map.of("provider", "gemini")))
                        : List.of(),
                List.of(), List.of(), List.of(), true, text, "markdown", List.of());
    }

    private String candidateText(String body, List<Integer> pages) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String modelText = root.at("/candidates/0/content/parts/0/text").asText("");
        if (modelText.isBlank()) {
            return "";
        }
        String json = stripFence(modelText);
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            Object formulas = parsed.get("formulas");
            if (formulas instanceof List<?> list) {
                List<String> lines = new ArrayList<>();
                int fallbackIndex = 0;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object latex = map.get("latex");
                        if (latex != null && !String.valueOf(latex).isBlank()) {
                            Object page = map.get("page");
                            lines.add("<!-- page: " + resolvePage(page, pages, fallbackIndex) + " -->\n" + latex);
                            fallbackIndex++;
                        }
                    } else if (item != null && !String.valueOf(item).isBlank()) {
                        lines.add("<!-- page: " + resolvePage(null, pages, fallbackIndex) + " -->\n" + item);
                        fallbackIndex++;
                    }
                }
                return String.join("\n\n", lines);
            }
        } catch (RuntimeException ignored) {
            // Fall through and parse line-by-line.
        }
        return modelText;
    }

    private int resolvePage(Object value, List<Integer> pages, int fallbackIndex) {
        Integer page = integer(value);
        if (pages == null || pages.isEmpty()) {
            return page == null ? 1 : page;
        }
        if (page != null && pages.contains(page)) {
            return page;
        }
        if (page != null && page >= 1 && page <= pages.size()) {
            return pages.get(page - 1);
        }
        return pages.get(Math.min(Math.max(0, fallbackIndex), pages.size() - 1));
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<ParsedBlock> blocks(String text, List<Integer> pages) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int fallbackPage = pages == null || pages.isEmpty() ? 1 : pages.get(0);
        int order = 0;
        int currentPage = fallbackPage;
        List<ParsedBlock> blocks = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            Integer marker = pageMarker(line);
            if (marker != null) {
                currentPage = marker;
                continue;
            }
            if (!looksLikeFormula(line)) {
                continue;
            }
            String sourceRef = "math-vision/page[" + currentPage + "]/formula[" + blocks.size() + "]";
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceRef", sourceRef);
            metadata.put("page", currentPage);
            metadata.put("mathVisionCorrectionOnly", true);
            metadata.put("mathVisionProvider", "gemini");
            metadata.put("confidence", 0.6d);
            blocks.add(ParsedBlock.text(sourceRef, BlockType.PARAGRAPH, normalizeFormula(line),
                    currentPage, order++, metadata));
        }
        return blocks;
    }

    private Integer pageMarker(String line) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\s*:?\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean looksLikeFormula(String line) {
        if (line.matches(".*[\\u4E00-\\u9FFF].*")) {
            return false;
        }
        return line.contains("$")
                || line.matches(".*\\\\(frac|sqrt|overline|mathrm|mathbf|mathbb|times|div|pm|leq|geq|neq)\\b.*")
                || line.matches(".*[A-Za-z0-9)][=+\\-*/^][A-Za-z0-9({].*");
    }

    private String normalizeFormula(String line) {
        String text = line.replace('−', '-').replace('ㅡ', '-').trim();
        if (text.startsWith("$") || text.startsWith("\\[")) {
            return text;
        }
        return "$" + text + "$";
    }

    private String stripFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
