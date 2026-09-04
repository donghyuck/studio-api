package studio.one.platform.ai.service.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationSnapshot;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationVerification;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationVerifier;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;

/**
 * Verifies Team migration by comparing logical source identities and existing vector counts.
 */
public final class RagTeamKnowledgeMigrationVerifier implements TeamKnowledgeMigrationVerifier {

    private final RagPipelineService ragPipelineService;

    public RagTeamKnowledgeMigrationVerifier(RagPipelineService ragPipelineService) {
        if (ragPipelineService == null) {
            throw new IllegalArgumentException("ragPipelineService must not be null");
        }
        this.ragPipelineService = ragPipelineService;
    }

    @Override
    public TeamKnowledgeMigrationVerification verify(
            TeamKnowledgeMigrationSnapshot expected,
            TeamKnowledgeManifest actual) {
        if (expected == null || actual == null) {
            throw new IllegalArgumentException("expected and actual must not be null");
        }
        if (!expected.teamId().equals(actual.teamId())) {
            throw new IllegalArgumentException("snapshot and manifest Team must match");
        }
        Set<String> expectedSources = canonical(expected.sources());
        Set<String> actualSources = canonical(actual.sources());
        List<String> missing = expectedSources.stream()
                .filter(source -> !actualSources.contains(source)).sorted().toList();
        List<String> unexpected = actualSources.stream()
                .filter(source -> !expectedSources.contains(source)).sorted().toList();
        List<String> vectorMismatches = new ArrayList<>();
        expected.vectorCounts().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(RagObjectScope::canonicalValue)))
                .forEach(entry -> {
                    long actualCount = ragPipelineService.countByObject(
                            entry.getKey().objectType(), entry.getKey().objectId());
                    if (actualCount != entry.getValue()) {
                        vectorMismatches.add(entry.getKey().canonicalValue()
                                + ":expected=" + entry.getValue() + ",actual=" + actualCount);
                    }
                });
        return new TeamKnowledgeMigrationVerification(
                missing.isEmpty() && unexpected.isEmpty() && vectorMismatches.isEmpty(),
                missing,
                unexpected,
                vectorMismatches);
    }

    private Set<String> canonical(List<TeamKnowledgeSourceRef> sources) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        sources.stream().map(TeamKnowledgeSourceRef::canonicalValue).sorted().forEach(values::add);
        return Set.copyOf(values);
    }
}
