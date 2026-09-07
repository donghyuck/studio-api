package studio.one.application.webknowledge.application;

import java.util.ArrayList;
import java.util.List;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;

/**
 * Contributes only completed indexed web revisions already owned by a Workspace.
 */
public final class WebKnowledgeTeamKnowledgeSourceContributor
        implements TeamKnowledgeSourceContributor {

    private final WebKnowledgeSourceJpaRepository sources;
    private final IndexedRagSourceProvider indexedSources;

    public WebKnowledgeTeamKnowledgeSourceContributor(
            WebKnowledgeSourceJpaRepository sources,
            IndexedRagSourceProvider indexedSources) {
        if (sources == null || indexedSources == null) {
            throw new IllegalArgumentException("sources and indexedSources are required");
        }
        this.sources = sources;
        this.indexedSources = indexedSources;
    }

    @Override
    public TeamKnowledgeSourceType sourceType() {
        return TeamKnowledgeSourceType.WEB_SOURCE;
    }

    @Override
    public List<TeamKnowledgeSourceRef> contribute(TeamKnowledgeContributionRequest request) {
        List<TeamKnowledgeSourceRef> result = new ArrayList<>();
        for (Long workspaceId : request.workspaceIds().stream().sorted().toList()) {
            sources.findByWorkspaceIdAndArchivedFalseOrderByUpdatedAtDesc(workspaceId).stream()
                    .map(source -> indexedSources.resolve(
                            source.sourceId(),
                            source.currentCorpusRevisionId() == null
                                    ? source.currentRevisionId()
                                    : source.currentCorpusRevisionId()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .forEach(source -> add(request, workspaceId, result, source));
        }
        return List.copyOf(result);
    }

    private void add(
            TeamKnowledgeContributionRequest request,
            Long workspaceId,
            List<TeamKnowledgeSourceRef> target,
            ResolvedIndexedRagSource source) {
        if (target.size() >= request.maxSources()) {
            throw new IllegalArgumentException("Team web source count exceeds maxSources");
        }
        target.add(new TeamKnowledgeSourceRef(
                request.teamId(),
                workspaceId,
                sourceType(),
                source.objectType(),
                source.objectId(),
                source.revisionId(),
                source.partitionIds()));
    }
}
