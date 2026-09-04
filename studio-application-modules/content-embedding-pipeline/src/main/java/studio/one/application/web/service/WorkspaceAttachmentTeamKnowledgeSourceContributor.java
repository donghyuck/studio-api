package studio.one.application.web.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;

/**
 * Discovers Workspace attachments while preserving their existing attachment RAG object IDs.
 */
public final class WorkspaceAttachmentTeamKnowledgeSourceContributor
        implements TeamKnowledgeSourceContributor {

    private static final String WORKSPACE_ATTACHMENT = "workspace-attachment";
    private static final String RAG_OBJECT_TYPE = "attachment";

    private final AttachmentService attachmentService;

    public WorkspaceAttachmentTeamKnowledgeSourceContributor(AttachmentService attachmentService) {
        if (attachmentService == null) {
            throw new IllegalArgumentException("attachmentService must not be null");
        }
        this.attachmentService = attachmentService;
    }

    @Override
    public TeamKnowledgeSourceType sourceType() {
        return TeamKnowledgeSourceType.ATTACHMENT;
    }

    @Override
    public List<TeamKnowledgeSourceRef> contribute(TeamKnowledgeContributionRequest request) {
        List<TeamKnowledgeSourceRef> sources = new ArrayList<>();
        for (Long workspaceId : request.workspaceIds().stream().sorted().toList()) {
            for (Attachment attachment : attachmentService.getAttachments(WORKSPACE_ATTACHMENT, workspaceId)) {
                if (sources.size() >= request.maxSources()) {
                    throw new IllegalArgumentException("Team attachment source count exceeds maxSources");
                }
                sources.add(new TeamKnowledgeSourceRef(
                        request.teamId(),
                        workspaceId,
                        sourceType(),
                        RAG_OBJECT_TYPE,
                        Long.toString(attachment.getAttachmentId()),
                        revisionToken(attachment),
                        java.util.Set.of()));
            }
        }
        return List.copyOf(sources);
    }

    private String revisionToken(Attachment attachment) {
        Instant updatedAt = attachment.getUpdatedAt();
        if (updatedAt != null) {
            return updatedAt.toString();
        }
        Instant createdAt = attachment.getCreatedAt();
        return (createdAt == null ? "legacy" : createdAt.toString()) + ":" + attachment.getSize();
    }
}
