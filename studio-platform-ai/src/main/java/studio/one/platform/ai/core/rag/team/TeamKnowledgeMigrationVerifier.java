package studio.one.platform.ai.core.rag.team;

/**
 * AI-side migration verification. It does not mutate Team, Workspace, source, or vector data.
 */
public interface TeamKnowledgeMigrationVerifier {

    TeamKnowledgeMigrationVerification verify(
            TeamKnowledgeMigrationSnapshot expected,
            TeamKnowledgeManifest actual);
}
