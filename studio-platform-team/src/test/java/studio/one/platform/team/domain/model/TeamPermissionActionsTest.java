package studio.one.platform.team.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamPermissionActionsTest {

    @Test
    void memberCannotManageMembersOrCreateWorkspaces() {
        assertThat(TeamPermissionActions.grantedActions(TeamRole.MEMBER))
                .contains(TeamPermissionActions.READ, TeamPermissionActions.KNOWLEDGE_READ, TeamPermissionActions.CHAT)
                .doesNotContain(TeamPermissionActions.MEMBER_MANAGE, TeamPermissionActions.WORKSPACE_CREATE);
    }

    @Test
    void adminCanManageButOnlyOwnerCanArchive() {
        assertThat(TeamPermissionActions.grantedActions(TeamRole.ADMIN))
                .contains(TeamPermissionActions.MEMBER_MANAGE, TeamPermissionActions.WORKSPACE_CREATE)
                .doesNotContain(TeamPermissionActions.ARCHIVE);
        assertThat(TeamPermissionActions.grantedActions(TeamRole.OWNER))
                .contains(TeamPermissionActions.ARCHIVE);
    }
}
