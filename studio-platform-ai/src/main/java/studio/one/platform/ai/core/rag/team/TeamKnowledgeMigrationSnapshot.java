package studio.one.platform.ai.core.rag.team;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.rag.RagObjectScope;

/**
 * Read-only pre-migration snapshot used to prove that source IDs and vectors were preserved.
 */
public record TeamKnowledgeMigrationSnapshot(
        String snapshotId,
        Long teamId,
        List<TeamKnowledgeSourceRef> sources,
        Map<RagObjectScope, Long> vectorCounts) {

    public TeamKnowledgeMigrationSnapshot {
        snapshotId = required(snapshotId, "snapshotId");
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        sources = TeamKnowledgeFingerprint.ordered(sources);
        vectorCounts = vectorCounts == null ? Map.of() : Map.copyOf(vectorCounts);
        if (sources.stream().anyMatch(source -> !teamId.equals(source.teamId()))) {
            throw new IllegalArgumentException("all snapshot sources must belong to the target Team");
        }
        if (!sources.stream().map(TeamKnowledgeSourceRef::objectScope).toList()
                .containsAll(vectorCounts.keySet())) {
            throw new IllegalArgumentException("vectorCounts contain an object outside snapshot sources");
        }
        if (vectorCounts.values().stream().anyMatch(count -> count == null || count < 0)) {
            throw new IllegalArgumentException("vectorCounts must be non-negative");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
