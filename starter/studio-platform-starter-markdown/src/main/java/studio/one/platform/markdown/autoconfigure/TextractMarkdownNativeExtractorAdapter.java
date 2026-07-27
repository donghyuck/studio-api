package studio.one.platform.markdown.autoconfigure;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownExtractPart;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptionsContext;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionProgressContext;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

public class TextractMarkdownNativeExtractorAdapter implements MarkdownNativeExtractorPort {
    private final FileContentExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final String extractorVersion;
    private final MarkdownRepository repository;
    private final ParsedFileNormalizedDocumentMapper normalizer;
    private final NormalizedMarkdownRenderer renderer;
    private final NormalizedDocumentQualityValidator qualityValidator;
    private final RenderedMarkdownPostProcessor markdownPostProcessor;

    public TextractMarkdownNativeExtractorAdapter(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, String extractorVersion) {
        this(extractionService, objectMapper, extractorVersion, null);
    }

    public TextractMarkdownNativeExtractorAdapter(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, String extractorVersion, MarkdownRepository repository) {
        this(extractionService, objectMapper, extractorVersion, repository,
                new ParsedFileNormalizedDocumentMapper(), new NormalizedMarkdownRenderer(),
                new NormalizedDocumentQualityValidator());
    }

    public TextractMarkdownNativeExtractorAdapter(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, String extractorVersion, MarkdownRepository repository,
            ParsedFileNormalizedDocumentMapper normalizer, NormalizedMarkdownRenderer renderer) {
        this(extractionService, objectMapper, extractorVersion, repository, normalizer, renderer,
                new NormalizedDocumentQualityValidator());
    }

    public TextractMarkdownNativeExtractorAdapter(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, String extractorVersion, MarkdownRepository repository,
            ParsedFileNormalizedDocumentMapper normalizer, NormalizedMarkdownRenderer renderer,
            NormalizedDocumentQualityValidator qualityValidator) {
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.extractorVersion = extractorVersion;
        this.repository = repository;
        this.normalizer = normalizer == null ? new ParsedFileNormalizedDocumentMapper() : normalizer;
        this.renderer = renderer == null ? new NormalizedMarkdownRenderer() : renderer;
        this.qualityValidator = qualityValidator == null ? new NormalizedDocumentQualityValidator() : qualityValidator;
        this.markdownPostProcessor = new RenderedMarkdownPostProcessor();
    }

    @Override
    public NativeExtraction extract(MarkdownSourcePort.MarkdownSource source, String revisionId) {
        List<MarkdownExtractPart> liveParts = Collections.synchronizedList(new ArrayList<>());
        if (repository != null) {
            repository.deleteExtractParts(revisionId);
        }
        var parsed = extractWithPartProgress(source, revisionId, liveParts);
        List<MarkdownLocator> locators = parsed.locators().stream()
                .map(locator -> new MarkdownLocator("mloc-" + UUID.randomUUID(), revisionId,
                        locator.type(), locator.number(), locator.title(), locator.startOffset(),
                        locator.endOffset(), locator.sourceRef(), writeJson(locator.metadata())))
                .toList();
        String fallbackMarkdown = parsed.markdown().isBlank() ? parsed.plainText() : parsed.markdown();
        NormalizedDocument normalized = normalizer.map(parsed, revisionId, source.fileName(),
                source.contentType(), fallbackMarkdown);
        String markdown = renderer.render(normalized, fallbackMarkdown);
        List<String> issues = qualityValidator.validate(normalized, markdown);
        normalized = markdownPostProcessor.withRenderedQuality(normalized, markdown, issues);
        List<MarkdownResource> resources = List.of(NormalizedDocumentSnapshot.resource(revisionId, normalized,
                NormalizedDocumentSnapshot.SOURCE_NATIVE, issues, objectMapper));
        List<MarkdownExtractPart> parts = liveParts.isEmpty()
                ? extractParts(revisionId, parsed.metadata())
                : List.copyOf(liveParts);
        String errorCode = null;
        String errorMessage = null;
        if (markdown.isBlank()
                && Boolean.TRUE.equals(parsed.metadata().get(PdfExtractionEngineSelector.KEY_LARGE_PDF_EXTRACTION))
                && number(parsed.metadata().get(PdfExtractionEngineSelector.KEY_LARGE_PDF_TEXT_LENGTH)) == 0) {
            errorCode = "NO_EXTRACTABLE_CONTENT";
            errorMessage = "No extractable text was produced by any PDF page range.";
        }
        return new NativeExtraction(markdown, extractorVersion, locators, resources, parts, errorCode, errorMessage);
    }

    private studio.one.platform.textract.domain.model.ParsedFile extractWithPartProgress(
            MarkdownSourcePort.MarkdownSource source,
            String revisionId,
            List<MarkdownExtractPart> liveParts) {
        Boolean ocrRequired = ocrRequired(revisionId);
        String ocrLanguage = ocrLanguage(revisionId);
        String ocrMode = ocrMode(revisionId);
        Boolean mathVisionCorrection = mathVisionCorrection(revisionId);
        try (PdfExtractionOptionsContext.Scope ignoredOptions = PdfExtractionOptionsContext.withOptions(ocrRequired,
                ocrLanguage, ocrMode, mathVisionCorrection);
                PdfExtractionProgressContext.Scope ignored = PdfExtractionProgressContext.withListener(partSummary -> {
            MarkdownExtractPart part = extractPart(revisionId, partSummary);
            if (part == null) {
                return;
            }
            liveParts.add(part);
            if (repository != null) {
                repository.saveExtractPart(part);
            }
        })) {
            return extractionService.parseStructured(source.contentType(), source.fileName(),
                    new ByteArrayInputStream(source.content()));
        }
    }

    private Boolean ocrRequired(String revisionId) {
        if (repository == null) {
            return null;
        }
        return repository.findRevision(revisionId)
                .map(revision -> booleanOption(revision.optionsJson(), "ocrRequired"))
                .orElse(null);
    }

    private String ocrLanguage(String revisionId) {
        if (repository == null) {
            return null;
        }
        return repository.findRevision(revisionId)
                .map(revision -> stringOption(revision.optionsJson(), "ocrLanguage"))
                .orElse(null);
    }

    private String ocrMode(String revisionId) {
        if (repository == null) {
            return null;
        }
        return repository.findRevision(revisionId)
                .map(revision -> stringOption(revision.optionsJson(), "ocrMode"))
                .orElse(null);
    }

    private Boolean mathVisionCorrection(String revisionId) {
        if (repository == null) {
            return null;
        }
        return repository.findRevision(revisionId)
                .map(revision -> booleanOption(revision.optionsJson(), "mathVisionCorrection"))
                .orElse(null);
    }

    private Boolean booleanOption(String optionsJson, String key) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(optionsJson).get(key);
            return value == null || value.isNull() ? null : value.asBoolean(false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringOption(String optionsJson, String key) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode value = objectMapper.readTree(optionsJson).get(key);
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asText(null);
            return text == null || text.isBlank() ? null : text.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<MarkdownExtractPart> extractParts(String revisionId, Map<String, Object> metadata) {
        Object value = metadata.get(PdfExtractionEngineSelector.KEY_LARGE_PDF_PARTS);
        if (!(value instanceof List<?> rawParts)) {
            return List.of();
        }
        Instant now = Instant.now();
        List<MarkdownExtractPart> parts = new ArrayList<>();
        for (Object rawPart : rawParts) {
            if (!(rawPart instanceof Map<?, ?> map)) {
                continue;
            }
            MarkdownExtractPart part = extractPart(revisionId, map);
            if (part == null) {
                continue;
            }
            parts.add(part);
        }
        return parts;
    }

    private MarkdownExtractPart extractPart(String revisionId, Map<?, ?> map) {
        int pageFrom = number(map.get("pageFrom"));
        int pageTo = number(map.get("pageTo"));
        if (pageFrom <= 0 || pageTo <= 0) {
            return null;
        }
        Instant now = Instant.now();
        return new MarkdownExtractPart(
                "mepart-" + UUID.randomUUID(),
                revisionId,
                pageFrom,
                pageTo,
                string(map.get("status"), "UNKNOWN"),
                string(map.get("engine"), "pymupdf4llm"),
                number(map.get("textLength")),
                string(map.get("markdownText"), null),
                string(map.get("errorCode"), null),
                string(map.get("errorMessage"), null),
                longValue(map.get("elapsedMs")),
                writeJson(map.get("metadata")),
                now,
                now,
                now);
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
