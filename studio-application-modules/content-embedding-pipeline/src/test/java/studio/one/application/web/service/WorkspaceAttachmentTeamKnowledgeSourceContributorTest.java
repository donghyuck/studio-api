package studio.one.application.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;

class WorkspaceAttachmentTeamKnowledgeSourceContributorTest {

    @Test
    void preservesAttachmentRagObjectIdForWorkspaceSources() {
        AttachmentService attachments = mock(AttachmentService.class);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(19L);
        when(attachment.getUpdatedAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(attachments.getAttachments("workspace-attachment", 2L)).thenReturn(List.of(attachment));

        var sources = new WorkspaceAttachmentTeamKnowledgeSourceContributor(attachments).contribute(
                new TeamKnowledgeContributionRequest(7L, Set.of(2L), 10));

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.objectType()).isEqualTo("attachment");
            assertThat(source.objectId()).isEqualTo("19");
            assertThat(source.workspaceId()).isEqualTo(2L);
        });
    }
}
