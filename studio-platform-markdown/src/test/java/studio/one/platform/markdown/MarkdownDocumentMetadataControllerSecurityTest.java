package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import studio.one.platform.markdown.web.MarkdownDocumentMetadataController;

class MarkdownDocumentMetadataControllerSecurityTest {

    @Test
    void metadataReadRequiresMarkdownAndSourceAttachmentRead() throws Exception {
        java.lang.annotation.Annotation authorization = java.util.Arrays.stream(MarkdownDocumentMetadataController.class
                .getMethod("metadata", String.class, String.class)
                .getAnnotations())
                .filter(annotation -> annotation.annotationType().getSimpleName().equals("PreAuthorize"))
                .findFirst()
                .orElseThrow();
        String expression = authorization.annotationType().getMethod("value").invoke(authorization).toString();

        assertThat(authorization).isNotNull();
        assertThat(expression)
                .contains("features:markdown")
                .contains("features:attachment")
                .contains("'read'");
    }

    @Test
    void metadataSummaryReadRequiresMarkdownAndSourceAttachmentRead() throws Exception {
        java.lang.annotation.Annotation authorization = java.util.Arrays.stream(MarkdownDocumentMetadataController.class
                .getMethod("metadataSummary", String.class, String.class)
                .getAnnotations())
                .filter(annotation -> annotation.annotationType().getSimpleName().equals("PreAuthorize"))
                .findFirst()
                .orElseThrow();
        String expression = authorization.annotationType().getMethod("value").invoke(authorization).toString();

        assertThat(authorization).isNotNull();
        assertThat(expression)
                .contains("features:markdown")
                .contains("features:attachment")
                .contains("'read'");
    }
}
