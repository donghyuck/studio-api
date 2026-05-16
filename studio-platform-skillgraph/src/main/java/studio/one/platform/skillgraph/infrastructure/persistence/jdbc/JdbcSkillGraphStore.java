package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.domain.port.SkillGraphStore;

@RequiredArgsConstructor
public class JdbcSkillGraphStore implements SkillGraphStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillRelation saveRelation(SkillRelation relation) {
        int updated = template.update("""
                UPDATE tb_skill_relation
                SET source_skill_id = :sourceSkillId,
                    target_skill_id = :targetSkillId,
                    relation_type = :relationType
                WHERE relation_id = :relationId
                """, params(relation));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_relation
                        (relation_id, source_skill_id, target_skill_id, relation_type, created_at)
                    VALUES
                        (:relationId, :sourceSkillId, :targetSkillId, :relationType, :createdAt)
                    """, params(relation));
        }
        return relation;
    }

    @Override
    public Optional<SkillRelation> findRelation(String relationId) {
        return template.query("""
                SELECT * FROM tb_skill_relation WHERE relation_id = :relationId
                """, Map.of("relationId", relationId), this::map).stream().findFirst();
    }

    @Override
    public List<SkillRelation> findRelations(String skillId, SkillRelationType type) {
        String skill = normalize(skillId);
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM tb_skill_relation
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (skill != null) {
            sql.append("  AND (source_skill_id = :skillId OR target_skill_id = :skillId)\n");
            params.addValue("skillId", skill);
        }
        if (type != null) {
            sql.append("  AND relation_type = :relationType\n");
            params.addValue("relationType", type.name());
        }
        sql.append("ORDER BY created_at DESC\n");
        return template.query(sql.toString(), params, this::map);
    }

    private MapSqlParameterSource params(SkillRelation relation) {
        return new MapSqlParameterSource()
                .addValue("relationId", relation.relationId())
                .addValue("sourceSkillId", relation.sourceSkillId())
                .addValue("targetSkillId", relation.targetSkillId())
                .addValue("relationType", relation.type().name())
                .addValue("createdAt", Timestamp.from(relation.createdAt()));
    }

    private SkillRelation map(ResultSet rs, int rowNum) throws SQLException {
        return new SkillRelation(
                rs.getString("relation_id"),
                rs.getString("source_skill_id"),
                rs.getString("target_skill_id"),
                SkillRelationType.valueOf(rs.getString("relation_type")),
                instant(rs.getTimestamp("created_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
