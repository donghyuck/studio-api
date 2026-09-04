package studio.one.platform.ai.core.rag.team;

import java.util.List;

/**
 * Domain-owned source discovery used to build Team corpus manifests without copying content.
 */
public interface TeamKnowledgeSourceContributor {

    TeamKnowledgeSourceType sourceType();

    List<TeamKnowledgeSourceRef> contribute(TeamKnowledgeContributionRequest request);
}
