package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;

class MarkdownDocumentMetadataRegenerationControllerTest {

    @Test
    void exposesMetadataOnlyReextractWithManageAndRagWritePermission() throws Exception {
        MarkdownMetadataBackfillService service = mock(MarkdownMetadataBackfillService.class);
        DocumentMetadataArtifact artifact = mock(DocumentMetadataArtifact.class);
        when(service.regenerate("mdoc-19", "mrev-2")).thenReturn(artifact);
        MarkdownDocumentMetadataRegenerationController controller =
                new MarkdownDocumentMetadataRegenerationController(service);

        assertThat(controller.reextract("mdoc-19", "mrev-2").getData()).isSameAs(artifact);
        verify(service).regenerate("mdoc-19", "mrev-2");

        Method method = MarkdownDocumentMetadataRegenerationController.class.getMethod(
                "reextract", String.class, String.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/{id}/metadata/reextract");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("features:markdown")
                .contains("'manage'")
                .contains("services:ai_rag")
                .contains("'write'");
    }
}
