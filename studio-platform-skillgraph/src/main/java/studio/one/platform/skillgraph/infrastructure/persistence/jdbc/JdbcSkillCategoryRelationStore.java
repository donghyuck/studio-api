package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelation;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelationType;
import studio.one.platform.skillgraph.domain.port.SkillCategoryRelationStore;

@RequiredArgsConstructor
public class JdbcSkillCategoryRelationStore implements SkillCategoryRelationStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillCategoryRelation save(SkillCategoryRelation relation) {
        int updated = template.update("""
                UPDATE tb_skill_category_relation
                SET source_category_id = :sourceCategoryId,
                    target_category_id = :targetCategoryId,
                    relation_type = :relationType,
                    score = :score,
                    confidence = :confidence,
                    reason = :reason,
                    updated_at = :updatedAt
                WHERE relation_id = :relationId
                """, params(relation));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_category_relation
                        (relation_id, source_category_id, target_category_id, relation_type, score, confidence, reason, created_at, updated_at)
                    VALUES
                        (:relationId, :sourceCategoryId, :targetCategoryId, :relationType, :score, :confidence, :reason, :createdAt, :updatedAt)
                    """, params(relation));
        }
        return relation;
    }

    @Override
    public Optional<SkillCategoryRelation> findById(String relationId) {
        return template.query("""
                SELECT * FROM tb_skill_category_relation WHERE relation_id = :relationId
                """, Map.of("relationId", relationId), this::map).stream().findFirst();
    }

    @Override
    public List<SkillCategoryRelation> findByCategoryIds(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return template.query("""
                    SELECT * FROM tb_skill_category_relation
                    ORDER BY score DESC, updated_at DESC
                    """, Map.of(), this::map);
        }
        return template.query("""
                SELECT * FROM tb_skill_category_relation
                WHERE source_category_id IN (:categoryIds)
                   OR target_category_id IN (:categoryIds)
                ORDER BY score DESC, updated_at DESC
                """, Map.of("categoryIds", categoryIds), this::map);
    }

    @Override
    public void delete(String relationId) {
        template.update("DELETE FROM tb_skill_category_relation WHERE relation_id = :relationId",
                Map.of("relationId", relationId));
    }

    private MapSqlParameterSource params(SkillCategoryRelation relation) {
        return new MapSqlParameterSource()
                .addValue("relationId", relation.relationId())
                .addValue("sourceCategoryId", relation.sourceCategoryId())
                .addValue("targetCategoryId", relation.targetCategoryId())
                .addValue("relationType", relation.relationType().name())
                .addValue("score", relation.score())
                .addValue("confidence", relation.confidence())
                .addValue("reason", relation.reason())
                .addValue("createdAt", Timestamp.from(relation.createdAt()))
                .addValue("updatedAt", Timestamp.from(relation.updatedAt()));
    }

    private SkillCategoryRelation map(ResultSet rs, int rowNum) throws SQLException {
        return new SkillCategoryRelation(
                rs.getString("relation_id"),
                rs.getString("source_category_id"),
                rs.getString("target_category_id"),
                SkillCategoryRelationType.valueOf(rs.getString("relation_type")),
                rs.getDouble("score"),
                rs.getDouble("confidence"),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
