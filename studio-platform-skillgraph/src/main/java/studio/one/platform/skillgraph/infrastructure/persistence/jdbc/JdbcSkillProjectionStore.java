package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;

@RequiredArgsConstructor
public class JdbcSkillProjectionStore implements SkillProjectionStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters) {
        template.update("DELETE FROM tb_skill_projection WHERE projection_id = :projectionId",
                Map.of("projectionId", projectionId));
        for (SkillProjection projection : projections) {
            template.update("""
                    INSERT INTO tb_skill_projection(projection_id, skill_id, x, y, cluster_id, created_at)
                    VALUES(:projectionId, :skillId, :x, :y, :clusterId, :createdAt)
                    """, projectionParams(projection));
        }
        if (clusters != null) {
            for (SkillCluster cluster : clusters) {
                saveCluster(cluster);
            }
        }
    }

    @Override
    public Page<SkillProjectionSummary> listProjections(Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<SkillProjectionSummary> content = template.query("""
                SELECT p.projection_id,
                       COUNT(*) AS item_count,
                       COUNT(DISTINCT p.cluster_id) AS cluster_count,
                       MIN(c.algorithm) AS algorithm,
                       MIN(p.created_at) AS created_at,
                       MAX(p.created_at) AS updated_at
                FROM tb_skill_projection p
                LEFT JOIN tb_skill_cluster c ON c.cluster_id = p.cluster_id
                GROUP BY p.projection_id
                ORDER BY MAX(p.created_at) DESC, p.projection_id
                LIMIT :limit OFFSET :offset
                """, params, this::mapProjectionSummary);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT projection_id
                    FROM tb_skill_projection
                    GROUP BY projection_id
                ) projection_summary
                """, Map.of(), Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Page<SkillProjection> findProjectionPoints(String projectionId, String clusterId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
                FROM tb_skill_projection
                WHERE projection_id = :projectionId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        String cluster = normalize(clusterId);
        if (cluster != null) {
            sql.append("  AND cluster_id = :clusterId\n");
            params.addValue("clusterId", cluster);
        }
        List<SkillProjection> content = template.query("""
                SELECT *, ROW_NUMBER() OVER (ORDER BY created_at, skill_id) - 1 AS display_order
                """ + sql + """
                ORDER BY display_order
                LIMIT :limit OFFSET :offset
                """, params, this::mapProjection);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                """ + sql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillCluster> findClusters(String projectionId) {
        return template.query("""
                SELECT * FROM tb_skill_cluster
                WHERE cluster_id IN (
                    SELECT DISTINCT cluster_id
                    FROM tb_skill_projection
                    WHERE projection_id = :projectionId AND cluster_id IS NOT NULL
                )
                ORDER BY label
                """, Map.of("projectionId", projectionId), this::mapCluster);
    }

    @Override
    public Optional<SkillProjection> findProjectionPoint(String projectionId, String skillId) {
        return template.query("""
                SELECT *, 0 AS display_order
                FROM tb_skill_projection
                WHERE projection_id = :projectionId AND skill_id = :skillId
                """, new MapSqlParameterSource()
                .addValue("projectionId", projectionId)
                .addValue("skillId", skillId), this::mapProjection).stream().findFirst();
    }

    private void saveCluster(SkillCluster cluster) {
        int updated = template.update("""
                UPDATE tb_skill_cluster
                SET label = :label,
                    algorithm = :algorithm,
                    item_count = :itemCount
                WHERE cluster_id = :clusterId
                """, clusterParams(cluster));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_cluster(cluster_id, label, algorithm, item_count, created_at)
                    VALUES(:clusterId, :label, :algorithm, :itemCount, :createdAt)
                    """, clusterParams(cluster));
        }
    }

    private MapSqlParameterSource projectionParams(SkillProjection projection) {
        return new MapSqlParameterSource()
                .addValue("projectionId", projection.projectionId())
                .addValue("skillId", projection.skillId())
                .addValue("x", projection.x())
                .addValue("y", projection.y())
                .addValue("clusterId", projection.clusterId())
                .addValue("createdAt", Timestamp.from(projection.createdAt()));
    }

    private MapSqlParameterSource clusterParams(SkillCluster cluster) {
        return new MapSqlParameterSource()
                .addValue("clusterId", cluster.clusterId())
                .addValue("label", cluster.label())
                .addValue("algorithm", cluster.algorithm())
                .addValue("itemCount", cluster.itemCount())
                .addValue("createdAt", Timestamp.from(cluster.createdAt()));
    }

    private SkillProjection mapProjection(ResultSet rs, int rowNum) throws SQLException {
        return new SkillProjection(
                rs.getString("projection_id"),
                rs.getString("skill_id"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getString("cluster_id"),
                rs.getInt("display_order"),
                instant(rs.getTimestamp("created_at")));
    }

    private SkillCluster mapCluster(ResultSet rs, int rowNum) throws SQLException {
        return new SkillCluster(
                rs.getString("cluster_id"),
                rs.getString("label"),
                rs.getString("algorithm"),
                rs.getInt("item_count"),
                instant(rs.getTimestamp("created_at")));
    }

    private SkillProjectionSummary mapProjectionSummary(ResultSet rs, int rowNum) throws SQLException {
        return new SkillProjectionSummary(
                rs.getString("projection_id"),
                rs.getInt("item_count"),
                rs.getInt("cluster_count"),
                rs.getString("algorithm"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
