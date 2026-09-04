package studio.one.platform.ai.core.rag.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TeamKnowledgeContractsTest {

    @Test
    void fingerprintIsOrderIndependentAndChangesWithRevision() {
        TeamKnowledgeSourceRef attachment = source(
                1L, TeamKnowledgeSourceType.ATTACHMENT, "attachment", "10", "rev-a");
        TeamKnowledgeSourceRef web = source(
                2L, TeamKnowledgeSourceType.WEB_SOURCE, "web_source", "src-1", "rev-web");

        String first = TeamKnowledgeFingerprint.create(7L, null, List.of(attachment, web));
        String reordered = TeamKnowledgeFingerprint.create(7L, null, List.of(web, attachment));
        String changed = TeamKnowledgeFingerprint.create(7L, null, List.of(
                source(1L, TeamKnowledgeSourceType.ATTACHMENT, "attachment", "10", "rev-b"), web));

        assertThat(reordered).isEqualTo(first);
        assertThat(changed).isNotEqualTo(first);
    }

    @Test
    void manifestRejectsForeignTeamAndForgedFingerprint() {
        TeamKnowledgeSourceRef source = source(
                1L, TeamKnowledgeSourceType.ATTACHMENT, "attachment", "10", "rev-a");

        assertThatThrownBy(() -> TeamKnowledgeManifest.create(
                8L, null, "corpus-1", "permission-1", List.of(source)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requested Team");

        assertThatThrownBy(() -> new TeamKnowledgeManifest(
                7L, null, "corpus-1", "forged", "permission-1", List.of(source)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corpusFingerprint");
    }

    private TeamKnowledgeSourceRef source(
            Long workspaceId,
            TeamKnowledgeSourceType sourceType,
            String objectType,
            String objectId,
            String revisionId) {
        return new TeamKnowledgeSourceRef(
                7L, workspaceId, sourceType, objectType, objectId, revisionId, Set.of());
    }
}
