package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.team.TeamCitationAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.web.cache.TeamRagCacheScope;

class TeamRagCitationGuardTest {

    @Test
    void rechecksCurrentTeamAndSourceAuthorizationForStoredCitation() {
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer authorizer = mock(TeamCitationAuthorizer.class);
        TeamKnowledgeManifest manifest = TeamKnowledgeManifest.create(
                7L, null, "corpus-2", "permission-2",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                        "attachment", "10", "rev-1", Set.of())));
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        when(authorizer.canRead(7L, 2L, "attachment", "10", "rev-1"))
                .thenReturn(true);
        TeamRagCitationGuard guard = new TeamRagCitationGuard(resolver, authorizer);

        boolean readable = guard.canReadStoredScope(
                TeamRagCacheScope.from(manifest),
                List.of(new TeamRagCitationRef(2L, "attachment", "10", "rev-1")));

        assertThat(readable).isTrue();
    }

    @Test
    void deniesStoredAnswerWhenCorpusOrPermissionVersionChanged() {
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer authorizer = mock(TeamCitationAuthorizer.class);
        TeamKnowledgeManifest manifest = TeamKnowledgeManifest.create(
                7L, null, "corpus-2", "permission-2",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                        "attachment", "10", "rev-1", Set.of())));
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        TeamRagCitationGuard guard = new TeamRagCitationGuard(resolver, authorizer);

        assertThat(guard.canReadStoredScope(
                new TeamRagCacheScope(
                        7L,
                        null,
                        "corpus-1",
                        manifest.corpusFingerprint(),
                        "permission-1"),
                List.of(new TeamRagCitationRef(2L, "attachment", "10", "rev-1"))))
                .isFalse();
    }

    @Test
    void deniesStoredCitationAfterMembershipOrSourceAccessIsRemoved() {
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer authorizer = mock(TeamCitationAuthorizer.class);
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.empty());
        TeamRagCitationGuard guard = new TeamRagCitationGuard(resolver, authorizer);

        assertThat(guard.canReadStoredScope(
                new TeamRagCacheScope(7L, null, "corpus-1", "fingerprint-old", "permission-1"),
                List.of(new TeamRagCitationRef(2L, "attachment", "10", "rev-1"))))
                .isFalse();
    }

    @Test
    void deniesStoredCitationWhenCurrentManifestMovedToAnotherRevision() {
        TeamRagScopeResolver resolver = mock(TeamRagScopeResolver.class);
        TeamCitationAuthorizer authorizer = mock(TeamCitationAuthorizer.class);
        TeamKnowledgeManifest manifest = TeamKnowledgeManifest.create(
                7L, null, "corpus-2", "permission-2",
                List.of(new TeamKnowledgeSourceRef(
                        7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                        "attachment", "10", "rev-2", Set.of())));
        when(resolver.resolveAuthorized(7L, null)).thenReturn(Optional.of(manifest));
        TeamRagCitationGuard guard = new TeamRagCitationGuard(resolver, authorizer);

        assertThat(guard.canReadStoredScope(
                new TeamRagCacheScope(7L, null, "corpus-1", "fingerprint-old", "permission-1"),
                List.of(new TeamRagCitationRef(2L, "attachment", "10", "rev-1"))))
                .isFalse();
    }
}
