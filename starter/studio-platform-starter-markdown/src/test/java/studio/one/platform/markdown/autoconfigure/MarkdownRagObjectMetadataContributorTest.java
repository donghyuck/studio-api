package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownPipelineExecutionStatus;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class MarkdownRagObjectMetadataContributorTest {

    @Test
    void contributesLatestMarkdownAndPipelineStateForAttachment() {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MarkdownDocument document = new MarkdownDocument("mdoc-1", 17L, "mrev-1", now, now);
        MarkdownRevision revision = new MarkdownRevision(
                "mrev-1", "mdoc-1", 17L, null, null,
                "TEXTRACT", "native", "{}", "options", "source", "content", "# content",
                "book.epub", "epub", "attachment", "17",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
        MarkdownPipelineExecution pipeline = new MarkdownPipelineExecution(
                "mrev-1", MarkdownPipelineExecutionStatus.RUNNING, MarkdownPipelineStage.RAG_INDEX,
                MarkdownPipelineStage.CHUNKING, 1, null, null, now, null, now);
        when(repository.findDocumentBySourceAttachmentId(17L)).thenReturn(Optional.of(document));
        when(repository.findRevisions("mdoc-1")).thenReturn(List.of(revision));
        when(repository.findPipelineExecution("mrev-1")).thenReturn(Optional.of(pipeline));

        Map<String, Object> result =
                new MarkdownRagObjectMetadataContributor(repository, new ObjectMapper())
                        .contribute("attachment", "17");

        assertThat((Map<String, Object>) result.get("markdown"))
                .containsEntry("exists", true)
                .containsEntry("documentId", "mdoc-1")
                .containsEntry("revisionStatus", "COMPLETED")
                .containsEntry("pipelineStatus", "RUNNING")
                .containsEntry("pipelineStage", "RAG_INDEX");
    }

    @Test
    void reportsMissingMarkdownForKnownAttachmentScope() {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findDocumentBySourceAttachmentId(17L)).thenReturn(Optional.empty());

        Map<String, Object> result =
                new MarkdownRagObjectMetadataContributor(repository, new ObjectMapper())
                        .contribute("attachment", "17");

        assertThat((Map<String, Object>) result.get("markdown")).containsEntry("exists", false);
    }
}
