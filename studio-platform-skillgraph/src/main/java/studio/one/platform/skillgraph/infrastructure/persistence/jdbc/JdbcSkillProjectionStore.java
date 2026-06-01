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
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillClusterMember;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillProjectionMetadata;
import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;

@RequiredArgsConstructor
public class JdbcSkillProjectionStore implements SkillProjectionStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters) {
        replaceProjection(projectionId, projections, clusters, null);
    }

    @Override
    public void replaceProjection(String projectionId, List<SkillProjection> projections, List<SkillCluster> clusters,
            SkillProjectionMetadata metadata) {
        replaceProjection(projectionId, projections, clusters, List.of(), metadata);
    }

    @Override
    @Transactional
    public void replaceProjection(
            String projectionId,
            List<SkillProjection> projections,
            List<SkillCluster> clusters,
            List<SkillClusterMember> members,
            SkillProjectionMetadata metadata) {
        template.update("DELETE FROM tb_skill_cluster_member WHERE projection_id = :projectionId",
                Map.of("projectionId", projectionId));
        template.update("DELETE FROM tb_skill_projection WHERE projection_id = :projectionId",
                Map.of("projectionId", projectionId));
        for (SkillProjection projection : projections) {
            template.update("""
                    INSERT INTO tb_skill_projection(
                        projection_id, skill_id, x, y, cluster_id,
                        skill_type, job_id, projection_type,
                        reduction_algorithm, projection_dimension, clustering_algorithm,
                        embedding_provider, embedding_model, embedding_dimension,
                        metadata,
                        created_at)
                    VALUES(
                        :projectionId, :skillId, :x, :y, :clusterId,
                        :skillType, :jobId, :projectionType,
                        :reductionAlgorithm, :projectionDimension, :clusteringAlgorithm,
                        :embeddingProvider, :embeddingModel, :embeddingDimension,
                        :metadata,
                        :createdAt)
                    """, projectionParams(projection, metadata));
        }
        if (clusters != null) {
            for (SkillCluster cluster : clusters) {
                saveCluster(cluster);
            }
        }
        if (members != null) {
            for (SkillClusterMember member : members) {
                saveMember(member);
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
                       COALESCE(MIN(p.clustering_algorithm), MIN(c.algorithm)) AS algorithm,
                       MIN(p.skill_type) AS skill_type,
                       MIN(p.job_id) AS job_id,
                       MIN(p.projection_type) AS projection_type,
                       MIN(p.reduction_algorithm) AS reduction_algorithm,
                       MIN(p.projection_dimension) AS projection_dimension,
                       MIN(p.embedding_provider) AS embedding_provider,
                       MIN(p.embedding_model) AS embedding_model,
                       MIN(p.embedding_dimension) AS embedding_dimension,
                       MIN(p.metadata) AS metadata,
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
    public Page<SkillClusterMember> findClusterMembers(String projectionId, String clusterId, Pageable pageable) {
        StringBuilder sql = new StringBuilder("""
                FROM tb_skill_cluster_member
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
        List<SkillClusterMember> content = template.query("""
                SELECT *
                """ + sql + """
                ORDER BY cluster_id, is_representative DESC, distance_to_centroid, skill_id
                LIMIT :limit OFFSET :offset
                """, params, this::mapMember);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                """ + sql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
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
                    item_count = :itemCount,
                    skill_type = :skillType,
                    job_id = :jobId,
                    cluster_label = :clusterLabel,
                    representative_skill_ids = :representativeSkillIds,
                    centroid_projection_id = :centroidProjectionId,
                    confidence = :confidence,
                    metadata = :metadata
                WHERE cluster_id = :clusterId
                """, clusterParams(cluster));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_cluster(
                        cluster_id, label, algorithm, item_count, skill_type, job_id, cluster_label,
                        representative_skill_ids, centroid_projection_id, confidence, metadata, created_at)
                    VALUES(
                        :clusterId, :label, :algorithm, :itemCount, :skillType, :jobId, :clusterLabel,
                        :representativeSkillIds, :centroidProjectionId, :confidence, :metadata, :createdAt)
                    """, clusterParams(cluster));
        }
    }

    private void saveMember(SkillClusterMember member) {
        template.update("""
                INSERT INTO tb_skill_cluster_member(
                    cluster_id, skill_id, embedding_id, projection_id,
                    membership_score, distance_to_centroid, is_representative)
                VALUES(
                    :clusterId, :skillId, :embeddingId, :projectionId,
                    :membershipScore, :distanceToCentroid, :representative)
                """, new MapSqlParameterSource()
                .addValue("clusterId", member.clusterId())
                .addValue("skillId", member.skillId())
                .addValue("embeddingId", member.embeddingId())
                .addValue("projectionId", member.projectionId())
                .addValue("membershipScore", member.membershipScore())
                .addValue("distanceToCentroid", member.distanceToCentroid())
                .addValue("representative", member.representative()));
    }

    private MapSqlParameterSource projectionParams(SkillProjection projection, SkillProjectionMetadata metadata) {
        return new MapSqlParameterSource()
                .addValue("projectionId", projection.projectionId())
                .addValue("skillId", projection.skillId())
                .addValue("x", projection.x())
                .addValue("y", projection.y())
                .addValue("clusterId", projection.clusterId())
                .addValue("skillType", metadata == null ? null : metadata.skillType())
                .addValue("jobId", metadata == null ? null : metadata.jobId())
                .addValue("projectionType", metadata == null ? null : metadata.projectionType())
                .addValue("reductionAlgorithm", metadata == null ? null : metadata.reductionAlgorithm())
                .addValue("projectionDimension", metadata == null ? null : metadata.projectionDimension())
                .addValue("clusteringAlgorithm", metadata == null ? null : metadata.clusteringAlgorithm())
                .addValue("embeddingProvider", metadata == null ? null : metadata.embeddingProvider())
                .addValue("embeddingModel", metadata == null ? null : metadata.embeddingModel())
                .addValue("embeddingDimension", metadata == null ? null : metadata.embeddingDimension())
                .addValue("metadata", metadata == null ? null : metadata.parameters())
                .addValue("createdAt", Timestamp.from(projection.createdAt()));
    }

    private MapSqlParameterSource clusterParams(SkillCluster cluster) {
        return new MapSqlParameterSource()
                .addValue("clusterId", cluster.clusterId())
                .addValue("label", cluster.label())
                .addValue("algorithm", cluster.algorithm())
                .addValue("itemCount", cluster.itemCount())
                .addValue("skillType", cluster.skillType())
                .addValue("jobId", cluster.jobId())
                .addValue("clusterLabel", cluster.clusterLabel())
                .addValue("representativeSkillIds", jsonArray(cluster.representativeSkillIds()))
                .addValue("centroidProjectionId", cluster.centroidProjectionId())
                .addValue("confidence", cluster.confidence())
                .addValue("metadata", cluster.metadata())
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
                hasColumn(rs, "skill_type") ? rs.getString("skill_type") : null,
                hasColumn(rs, "job_id") ? rs.getString("job_id") : null,
                hasColumn(rs, "cluster_label") ? (Integer) rs.getObject("cluster_label") : null,
                hasColumn(rs, "representative_skill_ids") ? parseJsonArray(rs.getString("representative_skill_ids")) : List.of(),
                hasColumn(rs, "centroid_projection_id") ? rs.getString("centroid_projection_id") : null,
                hasColumn(rs, "confidence") ? (Double) rs.getObject("confidence") : null,
                hasColumn(rs, "metadata") ? rs.getString("metadata") : null,
                instant(rs.getTimestamp("created_at")));
    }

    private SkillClusterMember mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new SkillClusterMember(
                rs.getString("cluster_id"),
                rs.getString("skill_id"),
                rs.getString("embedding_id"),
                rs.getString("projection_id"),
                rs.getDouble("membership_score"),
                rs.getDouble("distance_to_centroid"),
                rs.getBoolean("is_representative"));
    }

    private SkillProjectionSummary mapProjectionSummary(ResultSet rs, int rowNum) throws SQLException {
        return new SkillProjectionSummary(
                rs.getString("projection_id"),
                rs.getInt("item_count"),
                rs.getInt("cluster_count"),
                rs.getString("algorithm"),
                hasColumn(rs, "skill_type") ? rs.getString("skill_type") : null,
                hasColumn(rs, "job_id") ? rs.getString("job_id") : null,
                hasColumn(rs, "projection_type") ? rs.getString("projection_type") : null,
                rs.getString("reduction_algorithm"),
                hasColumn(rs, "projection_dimension") ? (Integer) rs.getObject("projection_dimension") : null,
                rs.getString("embedding_provider"),
                rs.getString("embedding_model"),
                (Integer) rs.getObject("embedding_dimension"),
                hasColumn(rs, "metadata") ? rs.getString("metadata") : null,
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private boolean hasColumn(ResultSet rs, String column) throws SQLException {
        int count = rs.getMetaData().getColumnCount();
        for (int index = 1; index <= count; index++) {
            if (column.equalsIgnoreCase(rs.getMetaData().getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private String jsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private List<String> parseJsonArray(String value) {
        if (value == null || value.isBlank() || "[]".equals(value.trim())) {
            return List.of();
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .map(item -> item.replaceAll("^\"|\"$", ""))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
