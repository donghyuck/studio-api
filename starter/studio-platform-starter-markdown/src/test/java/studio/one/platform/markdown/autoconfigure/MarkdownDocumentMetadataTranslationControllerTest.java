package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import studio.one.platform.markdown.application.DocumentMetadataTranslationArtifact;
import studio.one.platform.markdown.application.DocumentMetadataTranslationArtifact.GenerationMode;

class MarkdownDocumentMetadataTranslationControllerTest {

    @Test
    void exposesReadAndGenerateContractsWithSeparatePermissions() throws Exception {
        DefaultMarkdownMetadataTranslationService service = mock(DefaultMarkdownMetadataTranslationService.class);
        DocumentMetadataTranslationArtifact artifact = new DocumentMetadataTranslationArtifact(
                "translation-1",
                "mrev-2",
                "metadata-1",
                "sha256:source",
                "en",
                "ko",
                "한국어 요약",
                List.of("정책"),
                GenerationMode.TRANSLATED,
                "gemini-test",
                DefaultMarkdownMetadataTranslationService.PROMPT_VERSION,
                Instant.parse("2026-08-20T00:00:00Z"));
        when(service.find("mdoc-19", "mrev-2", "ko")).thenReturn(Optional.of(artifact));
        when(service.translate("mdoc-19", "mrev-2", "ko"))
                .thenReturn(new DefaultMarkdownMetadataTranslationService.Result(artifact, false));
        MarkdownDocumentMetadataTranslationController controller =
                new MarkdownDocumentMetadataTranslationController(service);

        var found = controller.get("mdoc-19", "mrev-2", "ko").getBody().getData();
        var generated = controller.translate("mdoc-19", "mrev-2", "ko").getData();

        assertThat(found.summary()).isEqualTo("한국어 요약");
        assertThat(found.reused()).isTrue();
        assertThat(generated.reused()).isFalse();
        verify(service).find("mdoc-19", "mrev-2", "ko");
        verify(service).translate("mdoc-19", "mrev-2", "ko");

        Method get = MarkdownDocumentMetadataTranslationController.class.getMethod(
                "get", String.class, String.class, String.class);
        assertThat(get.getAnnotation(GetMapping.class).value())
                .containsExactly("/{id}/metadata/translations");
        assertThat(get.getAnnotation(PreAuthorize.class).value())
                .contains("features:markdown")
                .contains("features:attachment")
                .contains("'read'");

        Method post = MarkdownDocumentMetadataTranslationController.class.getMethod(
                "translate", String.class, String.class, String.class);
        assertThat(post.getAnnotation(PostMapping.class).value())
                .containsExactly("/{id}/metadata/translations");
        assertThat(post.getAnnotation(PreAuthorize.class).value())
                .contains("features:markdown")
                .contains("'manage'")
                .contains("services:ai_rag")
                .contains("'write'");
    }
}
