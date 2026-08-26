package studio.one.platform.markdown.autoconfigure;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.markdown.application.DocumentMetadataTranslationArtifact;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.markdown.web.base-path:/api/markdown-documents}")
public class MarkdownDocumentMetadataTranslationController {

    private final DefaultMarkdownMetadataTranslationService service;

    MarkdownDocumentMetadataTranslationController(DefaultMarkdownMetadataTranslationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/metadata/translations")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read') "
            + "and @endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<Response>> get(
            @PathVariable String id,
            @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "ko") String language) {
        return service.find(id, revisionId, language)
                .map(value -> ResponseEntity.ok(ApiResponse.ok(Response.from(value, true))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/metadata/translations")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage') "
            + "and @endpointAuthz.can('services:ai_rag','write')")
    public ApiResponse<Response> translate(
            @PathVariable String id,
            @RequestParam(required = false) String revisionId,
            @RequestParam(defaultValue = "ko") String language) {
        DefaultMarkdownMetadataTranslationService.Result result = service.translate(id, revisionId, language);
        return ApiResponse.ok(Response.from(result.translation(), result.reused()));
    }

    public record Response(
            String translationId,
            String revisionId,
            String sourceArtifactId,
            String sourceSummaryHash,
            String sourceLanguage,
            String targetLanguage,
            String summary,
            List<String> keywords,
            String generationMode,
            String model,
            String promptVersion,
            Instant createdAt,
            boolean reused) {

        private static Response from(DocumentMetadataTranslationArtifact value, boolean reused) {
            return new Response(
                    value.translationId(),
                    value.revisionId(),
                    value.sourceArtifactId(),
                    value.sourceSummaryHash(),
                    value.sourceLanguage(),
                    value.targetLanguage(),
                    value.summary(),
                    value.keywords(),
                    value.generationMode().name(),
                    value.model(),
                    value.promptVersion(),
                    value.createdAt(),
                    reused);
        }
    }
}
