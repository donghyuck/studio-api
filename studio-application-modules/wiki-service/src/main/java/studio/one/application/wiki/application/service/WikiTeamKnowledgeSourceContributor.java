package studio.one.application.wiki.application.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;

import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageJpaRepository;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;

/**
 * Contributes current Wiki page revisions under the stable wiki_page/page ID scope.
 * Migration verification reports missing vectors until a Wiki indexer has populated that scope.
 */
public final class WikiTeamKnowledgeSourceContributor implements TeamKnowledgeSourceContributor {

    private static final String RAG_OBJECT_TYPE = "wiki_page";

    private final WikiPageJpaRepository pages;

    public WikiTeamKnowledgeSourceContributor(WikiPageJpaRepository pages) {
        if (pages == null) {
            throw new IllegalArgumentException("pages must not be null");
        }
        this.pages = pages;
    }

    @Override
    public TeamKnowledgeSourceType sourceType() {
        return TeamKnowledgeSourceType.WIKI;
    }

    @Override
    public List<TeamKnowledgeSourceRef> contribute(TeamKnowledgeContributionRequest request) {
        List<TeamKnowledgeSourceRef> result = new ArrayList<>();
        for (Long workspaceId : request.workspaceIds().stream().sorted().toList()) {
            int remaining = request.maxSources() - result.size();
            if (remaining <= 0) {
                throw new IllegalArgumentException("Team Wiki source count exceeds maxSources");
            }
            var page = pages.findByWorkspaceIdAndArchivedFalse(
                    workspaceId, PageRequest.of(0, remaining + 1));
            if (page.getNumberOfElements() > remaining || page.hasNext()) {
                throw new IllegalArgumentException("Team Wiki source count exceeds maxSources");
            }
            page.getContent().stream()
                    .filter(candidate -> candidate.getCurrentRevisionId() != null)
                    .map(candidate -> new TeamKnowledgeSourceRef(
                            request.teamId(),
                            workspaceId,
                            sourceType(),
                            RAG_OBJECT_TYPE,
                            candidate.getPageId().toString(),
                            candidate.getCurrentRevisionId().toString(),
                            java.util.Set.of()))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }
}
