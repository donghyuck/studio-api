package studio.one.platform.ai.core.rag.team;

/**
 * Live authorization check performed again when Team RAG citations are projected or reopened.
 */
public interface TeamCitationAuthorizer {

    boolean canRead(
            Long teamId,
            Long workspaceId,
            String objectType,
            String objectId,
            String revisionId);
}
