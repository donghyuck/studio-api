package studio.one.platform.team.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum TeamMigrationStatus {
    DRAFT,
    DRY_RUN_COMPLETED,
    READY,
    RUNNING,
    VERIFYING,
    VERIFIED,
    CUTOVER_COMPLETED,
    FAILED,
    ROLLBACK_REQUIRED,
    ROLLED_BACK;

    public boolean canTransitionTo(TeamMigrationStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    private Set<TeamMigrationStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(DRY_RUN_COMPLETED, FAILED);
            case DRY_RUN_COMPLETED -> EnumSet.of(READY, FAILED, ROLLED_BACK);
            case READY -> EnumSet.of(RUNNING, FAILED, ROLLED_BACK);
            case RUNNING -> EnumSet.of(VERIFYING, ROLLBACK_REQUIRED, FAILED);
            case VERIFYING -> EnumSet.of(VERIFIED, ROLLBACK_REQUIRED, FAILED);
            case VERIFIED -> EnumSet.of(CUTOVER_COMPLETED, ROLLBACK_REQUIRED);
            case CUTOVER_COMPLETED -> EnumSet.of(ROLLBACK_REQUIRED);
            case FAILED -> EnumSet.of(ROLLBACK_REQUIRED, ROLLED_BACK);
            case ROLLBACK_REQUIRED -> EnumSet.of(ROLLED_BACK, FAILED);
            case ROLLED_BACK -> EnumSet.noneOf(TeamMigrationStatus.class);
        };
    }
}
