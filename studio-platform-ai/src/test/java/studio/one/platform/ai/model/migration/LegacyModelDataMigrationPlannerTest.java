package studio.one.platform.ai.model.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LegacyModelDataMigrationPlannerTest {

    @Test
    void mapsOnlyExactLegacyContractAndReportsUnknownRows() {
        LegacyEmbeddingIdentity known = new LegacyEmbeddingIdentity(
                "gemini-768", "google-ai", "gemini-embedding-001", 768,
                "google-ai/gemini-embedding-001@768", 10);
        LegacyEmbeddingIdentity unknown = new LegacyEmbeddingIdentity(
                "legacy", "google-ai", "gemini-embedding-001", 768, null, 3);
        LegacyModelDataMapping mapping = new LegacyModelDataMapping(
                "google-ai/gemini-embedding-001@768", "gemini-embedding-001", 768,
                "humanities-text-v1", "google/gemini-embedding-001", "es:v1:test");

        var plan = new LegacyModelDataMigrationPlanner().plan(List.of(known, unknown), List.of(mapping), 0);

        assertThat(plan.mappedRowCount()).isEqualTo(10);
        assertThat(plan.unresolvedRowCount()).isEqualTo(3);
        assertThat(plan.decisions()).extracting(LegacyModelDataMigrationPlanner.Decision::status)
                .containsExactly(
                        LegacyModelDataMigrationPlanner.Status.MAPPED,
                        LegacyModelDataMigrationPlanner.Status.LEGACY_SPACE_UNKNOWN);
        assertThat(plan.safeToBackfill()).isTrue();
    }

    @Test
    void activeJobsBlockBackfill() {
        var plan = new LegacyModelDataMigrationPlanner().plan(List.of(), List.of(), 2);
        assertThat(plan.safeToBackfill()).isFalse();
    }
}
