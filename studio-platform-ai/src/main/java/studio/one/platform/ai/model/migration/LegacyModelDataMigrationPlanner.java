package studio.one.platform.ai.model.migration;

import java.util.ArrayList;
import java.util.List;

public final class LegacyModelDataMigrationPlanner {

    public MigrationPlan plan(
            List<LegacyEmbeddingIdentity> identities,
            List<LegacyModelDataMapping> mappings,
            long activeJobCount) {
        List<Decision> decisions = new ArrayList<>();
        long mapped = 0;
        long unresolved = 0;
        long ambiguous = 0;
        for (LegacyEmbeddingIdentity identity : identities == null ? List.<LegacyEmbeddingIdentity>of() : identities) {
            List<LegacyModelDataMapping> matches = mappings == null ? List.of()
                    : mappings.stream().filter(mapping -> mapping.matches(identity)).toList();
            Status status;
            LegacyModelDataMapping mapping = null;
            if (matches.size() == 1) {
                status = Status.MAPPED;
                mapping = matches.get(0);
                mapped += identity.rowCount();
            } else if (matches.isEmpty()) {
                status = Status.LEGACY_SPACE_UNKNOWN;
                unresolved += identity.rowCount();
            } else {
                status = Status.AMBIGUOUS;
                ambiguous += identity.rowCount();
            }
            decisions.add(new Decision(identity, status, mapping));
        }
        return new MigrationPlan(List.copyOf(decisions), mapped, unresolved, ambiguous, activeJobCount);
    }

    public enum Status {
        MAPPED,
        LEGACY_SPACE_UNKNOWN,
        AMBIGUOUS
    }

    public record Decision(LegacyEmbeddingIdentity identity, Status status, LegacyModelDataMapping mapping) {
    }

    public record MigrationPlan(
            List<Decision> decisions,
            long mappedRowCount,
            long unresolvedRowCount,
            long ambiguousRowCount,
            long activeJobCount) {

        public boolean safeToBackfill() {
            return activeJobCount == 0 && ambiguousRowCount == 0;
        }
    }
}
