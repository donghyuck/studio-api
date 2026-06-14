package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import studio.one.platform.markdown.application.MarkdownDocumentNotFoundException;
import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.web.MarkdownDocumentController;

class MarkdownDocumentControllerTest {

    @Test
    void wrapsSuccessfulAttachmentLookupInApiResponse() {
        MarkdownDocumentService service = mock(MarkdownDocumentService.class);
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(service.getDocumentBySourceAttachmentId(17L))
                .thenReturn(new MarkdownDocument("mdoc-1", 17L, "mrev-1", now, now));

        var response = new MarkdownDocumentController(service).getByAttachment(17L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().documentId()).isEqualTo("mdoc-1");
    }

    @Test
    void returnsProblemDetailsAndNotFoundForMissingAttachmentHistory() {
        MarkdownDocumentController controller =
                new MarkdownDocumentController(mock(MarkdownDocumentService.class));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/markdown-documents/by-attachment/17");

        var response = controller.notFound(
                new MarkdownDocumentNotFoundException(
                        "Markdown document not found for attachment: 17"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getCode()).isEqualTo("markdown.document.not-found");
    }
}
