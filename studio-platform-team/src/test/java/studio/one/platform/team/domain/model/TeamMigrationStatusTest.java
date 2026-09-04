package studio.one.platform.team.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamMigrationStatusTest {
    @Test
    void supportsTheApprovedForwardStateMachine() {
        assertThat(TeamMigrationStatus.DRAFT.canTransitionTo(TeamMigrationStatus.DRY_RUN_COMPLETED)).isTrue();
        assertThat(TeamMigrationStatus.DRY_RUN_COMPLETED.canTransitionTo(TeamMigrationStatus.READY)).isTrue();
        assertThat(TeamMigrationStatus.READY.canTransitionTo(TeamMigrationStatus.RUNNING)).isTrue();
        assertThat(TeamMigrationStatus.RUNNING.canTransitionTo(TeamMigrationStatus.VERIFYING)).isTrue();
        assertThat(TeamMigrationStatus.VERIFYING.canTransitionTo(TeamMigrationStatus.VERIFIED)).isTrue();
        assertThat(TeamMigrationStatus.VERIFIED.canTransitionTo(TeamMigrationStatus.CUTOVER_COMPLETED)).isTrue();
    }

    @Test
    void rollbackIsTerminalAndInvalidSkipsAreRejected() {
        assertThat(TeamMigrationStatus.RUNNING.canTransitionTo(TeamMigrationStatus.CUTOVER_COMPLETED)).isFalse();
        assertThat(TeamMigrationStatus.CUTOVER_COMPLETED.canTransitionTo(TeamMigrationStatus.ROLLBACK_REQUIRED)).isTrue();
        assertThat(TeamMigrationStatus.ROLLBACK_REQUIRED.canTransitionTo(TeamMigrationStatus.ROLLED_BACK)).isTrue();
        assertThat(TeamMigrationStatus.ROLLED_BACK.canTransitionTo(TeamMigrationStatus.RUNNING)).isFalse();
    }
}
