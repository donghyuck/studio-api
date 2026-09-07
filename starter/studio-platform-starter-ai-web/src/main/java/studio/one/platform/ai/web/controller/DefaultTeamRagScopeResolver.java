package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeFingerprint;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;

/**
 * Default fail-closed Team scope adapter for the current authenticated principal.
 */
@Slf4j
public final class DefaultTeamRagScopeResolver implements TeamRagScopeResolver {

    private final PrincipalResolver principalResolver;
    private final TeamAuthorizationPort teamAuthorization;
    private final WorkspaceTreeService workspaceTreeService;
    private final List<TeamKnowledgeSourceContributor> contributors;
    private final int maxWorkspaces;
    private final int maxSources;

    public DefaultTeamRagScopeResolver(
            PrincipalResolver principalResolver,
            TeamAuthorizationPort teamAuthorization,
            WorkspaceTreeService workspaceTreeService,
            List<TeamKnowledgeSourceContributor> contributors,
            int maxWorkspaces,
            int maxSources) {
        if (principalResolver == null || teamAuthorization == null || workspaceTreeService == null) {
            throw new IllegalArgumentException("principal, Team authorization, and Workspace tree are required");
        }
        if (maxWorkspaces <= 0 || maxWorkspaces > 10_000 || maxSources <= 0) {
            throw new IllegalArgumentException("Team scope limits must be positive and bounded");
        }
        this.principalResolver = principalResolver;
        this.teamAuthorization = teamAuthorization;
        this.workspaceTreeService = workspaceTreeService;
        this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
        this.maxWorkspaces = maxWorkspaces;
        this.maxSources = maxSources;
    }

    @Override
    public Optional<TeamKnowledgeManifest> resolveAuthorized(Long teamId, Long workspaceId) {
        if (teamId == null || teamId <= 0 || workspaceId != null && workspaceId <= 0) {
            return Optional.empty();
        }
        try {
            ApplicationPrincipal principal = principalResolver.currentOrNull();
            if (principal == null || principal.getUserId() == null || principal.getUserId() <= 0) {
                return Optional.empty();
            }
            Long userId = principal.getUserId();
            Set<String> actions = teamAuthorization.grantedActions(teamId, userId);
            if (!teamAuthorization.isMember(teamId, userId)
                    || actions == null
                    || !actions.contains(TeamPermissionActions.KNOWLEDGE_READ)) {
                return Optional.empty();
            }
            WorkspaceAccessContext actor = new WorkspaceAccessContext(
                    userId, principal.getUsername(), false);
            List<Long> workspaceIds = workspaceTreeService.getAuthorizedTeamWorkspaceIds(
                    teamId, workspaceId, maxWorkspaces, actor);
            if (workspaceIds.isEmpty()) {
                return Optional.empty();
            }
            Set<Long> authorizedWorkspaces = Set.copyOf(workspaceIds);
            List<TeamKnowledgeSourceRef> collected = new ArrayList<>();
            List<TeamKnowledgeSourceContributor> activeContributors = contributors();
            log.debug("Resolving Team RAG sources: teamId={}, workspaceCount={}, contributorCount={}",
                    teamId, workspaceIds.size(), activeContributors.size());
            for (TeamKnowledgeSourceContributor contributor : activeContributors) {
                List<TeamKnowledgeSourceRef> contributed;
                try {
                    contributed = contributor.contribute(new TeamKnowledgeContributionRequest(
                            teamId, new LinkedHashSet<>(workspaceIds), maxSources));
                } catch (RuntimeException ex) {
                    log.warn("Team RAG source contribution failed: teamId={}, contributor={}, errorType={}",
                            teamId, contributor.getClass().getSimpleName(), ex.getClass().getSimpleName());
                    return Optional.empty();
                }
                if (contributed == null
                        || contributed.stream().anyMatch(source -> !teamId.equals(source.teamId())
                                || !authorizedWorkspaces.contains(source.workspaceId()))) {
                    return Optional.empty();
                }
                collected.addAll(contributed);
                log.debug("Team RAG source contribution completed: teamId={}, contributor={}, sourceCount={}",
                        teamId, contributor.getClass().getSimpleName(), contributed.size());
                if (collected.size() > maxSources) {
                    return Optional.empty();
                }
            }
            List<TeamKnowledgeSourceRef> sources = collected.stream().distinct().toList();
            if (sources.size() > maxSources) {
                return Optional.empty();
            }
            String fingerprint = TeamKnowledgeFingerprint.create(teamId, workspaceId, sources);
            String corpusRevisionId = "tcorpus-" + fingerprint.substring(0, 24);
            return Optional.of(new TeamKnowledgeManifest(
                    teamId,
                    workspaceId,
                    corpusRevisionId,
                    fingerprint,
                    Long.toString(teamAuthorization.permissionVersion(teamId)),
                    sources));
        } catch (RuntimeException ex) {
            log.warn("Team RAG scope resolution failed: teamId={}, workspaceId={}, errorType={}",
                    teamId, workspaceId, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<TeamKnowledgeSourceContributor> contributors() {
        return contributors;
    }
}
