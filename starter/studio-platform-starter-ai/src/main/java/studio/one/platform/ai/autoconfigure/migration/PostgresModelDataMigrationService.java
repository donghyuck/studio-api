package studio.one.platform.ai.autoconfigure.migration;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import studio.one.platform.ai.model.migration.LegacyEmbeddingIdentity;
import studio.one.platform.ai.model.migration.LegacyModelDataMapping;
import studio.one.platform.ai.model.migration.LegacyModelDataMigrationPlanner;

/**
 * Explicit operational service for PostgreSQL model identity migration.
 * No mutation is performed during application startup.
 */
public final class PostgresModelDataMigrationService {

    private static final String GROUP_SQL = """
            SELECT metadata ->> 'embeddingProfileId' AS profile_id,
                   metadata ->> 'embeddingProvider' AS provider,
                   metadata ->> 'embeddingModel' AS model,
                   embedding_dimension,
                   metadata ->> 'embeddingSpaceId' AS legacy_space_id,
                   COUNT(*) AS row_count
            FROM tb_ai_document_chunk
            GROUP BY metadata ->> 'embeddingProfileId',
                     metadata ->> 'embeddingProvider',
                     metadata ->> 'embeddingModel',
                     embedding_dimension,
                     metadata ->> 'embeddingSpaceId'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final LegacyModelDataMigrationPlanner planner = new LegacyModelDataMigrationPlanner();

    public PostgresModelDataMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LegacyModelDataMigrationPlanner.MigrationPlan dryRun(List<LegacyModelDataMapping> mappings) {
        List<LegacyEmbeddingIdentity> identities = jdbcTemplate.query(GROUP_SQL, (resultSet, rowNum) ->
                new LegacyEmbeddingIdentity(
                        resultSet.getString("profile_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("model"),
                        resultSet.getObject("embedding_dimension", Integer.class),
                        resultSet.getString("legacy_space_id"),
                        resultSet.getLong("row_count")));
        Long activeJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_ai_rag_index_job WHERE status IN ('PENDING','RUNNING')", Long.class);
        return planner.plan(identities, mappings, activeJobs == null ? 0 : activeJobs);
    }

    public long backfillKnown(
            LegacyModelDataMigrationPlanner.MigrationPlan plan,
            int batchSize) {
        if (plan == null || !plan.safeToBackfill()) {
            throw new IllegalStateException("Migration plan is not safe to backfill");
        }
        int effectiveBatchSize = Math.max(1, Math.min(batchSize, 10_000));
        long updated = 0;
        for (LegacyModelDataMigrationPlanner.Decision decision : plan.decisions()) {
            if (decision.status() != LegacyModelDataMigrationPlanner.Status.MAPPED) {
                continue;
            }
            int batch;
            do {
                batch = updateBatch(decision.mapping(), effectiveBatchSize);
                updated += batch;
            } while (batch == effectiveBatchSize);
        }
        return updated;
    }

    private int updateBatch(LegacyModelDataMapping mapping, int batchSize) {
        return jdbcTemplate.update("""
                WITH target AS (
                    SELECT ctid
                    FROM tb_ai_document_chunk
                    WHERE metadata ->> 'embeddingSpaceId' = ?
                      AND metadata ->> 'embeddingModel' = ?
                      AND embedding_dimension = ?
                      AND COALESCE(metadata ->> 'embeddingSpaceIdV2', '') = ''
                    LIMIT ?
                )
                UPDATE tb_ai_document_chunk chunk
                SET metadata = COALESCE(chunk.metadata, '{}'::jsonb) || jsonb_build_object(
                    'embeddingSpaceIdV2', ?,
                    'embeddingDeploymentId', ?,
                    'embeddingCatalogId', ?,
                    'embeddingContractVersion', 'v1')
                WHERE chunk.ctid IN (SELECT ctid FROM target)
                """,
                mapping.legacySpaceId(), mapping.model(), mapping.dimension(), batchSize,
                mapping.embeddingSpaceId(), mapping.deploymentId(), mapping.catalogId());
    }
}
