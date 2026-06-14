package studio.one.platform.markdown.autoconfigure;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

public class TextractMarkdownNativeExtractorAdapter implements MarkdownNativeExtractorPort {
    private final FileContentExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final String extractorVersion;

    public TextractMarkdownNativeExtractorAdapter(FileContentExtractionService extractionService,
            ObjectMapper objectMapper, String extractorVersion) {
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.extractorVersion = extractorVersion;
    }

    @Override
    public NativeExtraction extract(MarkdownSourcePort.MarkdownSource source, String revisionId) {
        var parsed = extractionService.parseStructured(source.contentType(), source.fileName(),
                new ByteArrayInputStream(source.content()));
        List<MarkdownLocator> locators = parsed.locators().stream()
                .map(locator -> new MarkdownLocator("mloc-" + UUID.randomUUID(), revisionId,
                        locator.type(), locator.number(), locator.title(), locator.startOffset(),
                        locator.endOffset(), locator.sourceRef(), writeJson(locator.metadata())))
                .toList();
        String markdown = parsed.markdown().isBlank() ? parsed.plainText() : parsed.markdown();
        return new NativeExtraction(markdown, extractorVersion, locators, List.of());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
