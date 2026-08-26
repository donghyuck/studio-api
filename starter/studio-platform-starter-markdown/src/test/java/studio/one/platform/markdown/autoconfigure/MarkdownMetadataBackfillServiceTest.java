package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.MetadataEnrichmentMode;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownMetadataEnrichmentPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class MarkdownMetadataBackfillServiceTest {

    @Test
    void regeneratesOnlyTheCurrentCompletedRevisionWithRequiredEnrichment() {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        MarkdownMetadataEnrichmentPort enrichment = mock(MarkdownMetadataEnrichmentPort.class);
        MarkdownDocument document = mock(MarkdownDocument.class);
        MarkdownRevision revision = mock(MarkdownRevision.class);
        DocumentMetadataArtifact artifact = mock(DocumentMetadataArtifact.class);
        when(document.currentRevisionId()).thenReturn("mrev-2");
        when(revision.revisionId()).thenReturn("mrev-2");
        when(revision.status()).thenReturn(MarkdownRevisionStatus.COMPLETED);
        when(revision.optionsJson()).thenReturn("{}");
        when(repository.findDocument("mdoc-19")).thenReturn(Optional.of(document));
        when(repository.findRevision("mrev-2")).thenReturn(Optional.of(revision));
        when(enrichment.regenerate(any(), any())).thenReturn(artifact);

        MarkdownMetadataBackfillService service = service(repository, enrichment);

        assertThat(service.regenerate("mdoc-19", "mrev-2")).isSameAs(artifact);
        ArgumentCaptor<MarkdownPipelineOptions> options = ArgumentCaptor.forClass(MarkdownPipelineOptions.class);
        verify(enrichment).regenerate(org.mockito.Mockito.eq(revision), options.capture());
        assertThat(options.getValue().enrichmentMode()).isEqualTo(MetadataEnrichmentMode.REQUIRED);
    }

    @Test
    void rejectsAStaleRevisionBeforeCallingEnrichment() {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        MarkdownMetadataEnrichmentPort enrichment = mock(MarkdownMetadataEnrichmentPort.class);
        MarkdownDocument document = mock(MarkdownDocument.class);
        when(document.currentRevisionId()).thenReturn("mrev-2");
        when(repository.findDocument("mdoc-19")).thenReturn(Optional.of(document));

        MarkdownMetadataBackfillService service = service(repository, enrichment);

        assertThatThrownBy(() -> service.regenerate("mdoc-19", "mrev-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current revision");
        verifyNoInteractions(enrichment);
    }

    @Test
    void rejectsTheResultWhenTheCurrentRevisionChangesDuringRegeneration() {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        MarkdownMetadataEnrichmentPort enrichment = mock(MarkdownMetadataEnrichmentPort.class);
        MarkdownDocument originalDocument = mock(MarkdownDocument.class);
        MarkdownDocument updatedDocument = mock(MarkdownDocument.class);
        MarkdownRevision revision = mock(MarkdownRevision.class);
        DocumentMetadataArtifact artifact = mock(DocumentMetadataArtifact.class);
        when(originalDocument.currentRevisionId()).thenReturn("mrev-2");
        when(updatedDocument.currentRevisionId()).thenReturn("mrev-3");
        when(revision.revisionId()).thenReturn("mrev-2");
        when(revision.status()).thenReturn(MarkdownRevisionStatus.COMPLETED);
        when(revision.optionsJson()).thenReturn("{}");
        when(repository.findDocument("mdoc-19"))
                .thenReturn(Optional.of(originalDocument), Optional.of(updatedDocument));
        when(repository.findRevision("mrev-2")).thenReturn(Optional.of(revision));
        when(enrichment.regenerate(any(), any())).thenReturn(artifact);

        MarkdownMetadataBackfillService service = service(repository, enrichment);

        assertThatThrownBy(() -> service.regenerate("mdoc-19", "mrev-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed during metadata regeneration");
        verify(enrichment).regenerate(org.mockito.Mockito.eq(revision), any());
    }

    @SuppressWarnings("unchecked")
    private MarkdownMetadataBackfillService service(
            MarkdownRepository repository,
            MarkdownMetadataEnrichmentPort enrichment) {
        ObjectProvider<RagPipelineService> pipelineProvider = mock(ObjectProvider.class);
        when(pipelineProvider.getIfAvailable()).thenReturn(null);
        return new MarkdownMetadataBackfillService(
                mock(NamedParameterJdbcTemplate.class),
                repository,
                enrichment,
                new ObjectMapper(),
                mock(TaskExecutor.class),
                pipelineProvider);
    }
}
