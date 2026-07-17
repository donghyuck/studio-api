package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.domain.MarkdownResource;

public class DefaultMarkdownNormalizationPort implements MarkdownNormalizationPort {
    private final MarkdownTextBlockParser parser;
    private final NormalizedMarkdownRenderer renderer;
    private final NormalizedDocumentQualityValidator qualityValidator;
    private final RenderedMarkdownPostProcessor markdownPostProcessor;
    private final ObjectMapper objectMapper;

    public DefaultMarkdownNormalizationPort(MarkdownTextBlockParser parser,
            NormalizedMarkdownRenderer renderer,
            ObjectMapper objectMapper) {
        this.parser = parser;
        this.renderer = renderer;
        this.qualityValidator = new NormalizedDocumentQualityValidator();
        this.markdownPostProcessor = new RenderedMarkdownPostProcessor();
        this.objectMapper = objectMapper;
    }

    @Override
    public NormalizationResult normalize(NormalizationRequest request) {
        List<MarkdownResource> resourcesWithProfile = NormalizedDocumentSnapshot.withDocumentProfile(
                request.resources(), request.requestedDocumentProfile(), request.resolvedDocumentProfile(),
                request.documentProfileVersion(), objectMapper);
        if (resourcesWithProfile.stream()
                .anyMatch(resource -> RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType()))) {
            return new NormalizationResult(markdownPostProcessor.postProcess(request.markdown()),
                    request.locators(), resourcesWithProfile);
        }
        List<String> issues = new ArrayList<>();
        NormalizedDocument document = parser.parse(request.markdown(), request.revisionId(),
                request.sourceFileName(), request.sourceFormat());
        document = NormalizedDocumentSnapshot.withDocumentProfile(document,
                request.requestedDocumentProfile(), request.resolvedDocumentProfile(),
                request.documentProfileVersion());
        if (document.blocks().isEmpty()) {
            issues.add("NO_NORMALIZED_BLOCKS");
        }
        String rendered = renderer.render(document, request.markdown());
        if (rendered.isBlank() && !request.markdown().isBlank()) {
            rendered = request.markdown();
            issues.add("RENDERED_MARKDOWN_BLANK");
        }
        issues.addAll(qualityValidator.validate(document, rendered));
        document = markdownPostProcessor.withRenderedQuality(document, rendered, issues);
        List<MarkdownResource> resources = new ArrayList<>(resourcesWithProfile.stream()
                .filter(resource -> !RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType()))
                .toList());
        resources.add(NormalizedDocumentSnapshot.resource(request.revisionId(), document,
                request.normalizationSource(), issues, objectMapper));
        return new NormalizationResult(rendered, request.locators(), resources);
    }
}
