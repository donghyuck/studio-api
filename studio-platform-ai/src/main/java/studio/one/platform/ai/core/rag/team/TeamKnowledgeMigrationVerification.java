package studio.one.platform.ai.core.rag.team;

import java.util.List;

/**
 * Fail-closed verification result. Any source or vector mismatch blocks cutover.
 */
public record TeamKnowledgeMigrationVerification(
        boolean valid,
        List<String> missingSources,
        List<String> unexpectedSources,
        List<String> vectorCountMismatches) {

    public TeamKnowledgeMigrationVerification {
        missingSources = copy(missingSources);
        unexpectedSources = copy(unexpectedSources);
        vectorCountMismatches = copy(vectorCountMismatches);
        valid = missingSources.isEmpty() && unexpectedSources.isEmpty() && vectorCountMismatches.isEmpty();
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
