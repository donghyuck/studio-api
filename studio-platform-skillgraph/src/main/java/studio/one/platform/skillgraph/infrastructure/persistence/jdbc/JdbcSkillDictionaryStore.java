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
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

@RequiredArgsConstructor
public class JdbcSkillDictionaryStore implements SkillDictionaryStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillDictionary save(SkillDictionary skill) {
        int updated = template.update("""
                UPDATE tb_skill_dictionary
                SET name = :name,
                    normalized_name = :normalizedName,
                    category_id = :categoryId,
                    status = :status,
                    updated_at = :updatedAt
                WHERE skill_id = :skillId
                """, skillParams(skill));
        if (updated > 0) {
            return skill;
        }
        template.update("""
                INSERT INTO tb_skill_dictionary
                    (skill_id, name, normalized_name, category_id, status, created_at, updated_at)
                VALUES
                    (:skillId, :name, :normalizedName, :categoryId, :status, :createdAt, :updatedAt)
                """, skillParams(skill));
        return skill;
    }

    @Override
    public Optional<SkillDictionary> findById(String skillId) {
        return queryOne("SELECT * FROM tb_skill_dictionary WHERE skill_id = :skillId", Map.of("skillId", skillId));
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedName(String normalizedName) {
        return queryOne("SELECT * FROM tb_skill_dictionary WHERE normalized_name = :normalizedName",
                Map.of("normalizedName", normalizedName));
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedAlias(String normalizedAlias) {
        return queryOne("""
                SELECT d.*
                FROM tb_skill_dictionary d
                JOIN tb_skill_alias a ON a.skill_id = d.skill_id
                WHERE a.normalized_alias = :normalizedAlias
                """, Map.of("normalizedAlias", normalizedAlias));
    }

    @Override
    public SkillAlias saveAlias(SkillAlias alias) {
        int updated = template.update("""
                UPDATE tb_skill_alias
                SET skill_id = :skillId,
                    alias = :alias,
                    normalized_alias = :normalizedAlias
                WHERE alias_id = :aliasId
                """, aliasParams(alias));
        if (updated > 0) {
            return alias;
        }
        template.update("""
                INSERT INTO tb_skill_alias
                    (alias_id, skill_id, alias, normalized_alias, created_at)
                VALUES
                    (:aliasId, :skillId, :alias, :normalizedAlias, :createdAt)
                """, aliasParams(alias));
        return alias;
    }

    @Override
    public List<SkillDictionary> search(String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase();
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT * FROM tb_skill_dictionary
                WHERE (:q = '' OR LOWER(name) LIKE :likeQ OR normalized_name LIKE :likeQ)
                ORDER BY name
                LIMIT :limit
                """, Map.of("q", query, "likeQ", "%" + query + "%", "limit", max), this::mapSkill);
    }

    private Optional<SkillDictionary> queryOne(String sql, Map<String, ?> params) {
        return template.query(sql, params, this::mapSkill).stream().findFirst();
    }

    private MapSqlParameterSource skillParams(SkillDictionary skill) {
        return new MapSqlParameterSource()
                .addValue("skillId", skill.skillId())
                .addValue("name", skill.name())
                .addValue("normalizedName", skill.normalizedName())
                .addValue("categoryId", skill.categoryId())
                .addValue("status", skill.status())
                .addValue("createdAt", Timestamp.from(skill.createdAt()))
                .addValue("updatedAt", Timestamp.from(skill.updatedAt()));
    }

    private MapSqlParameterSource aliasParams(SkillAlias alias) {
        return new MapSqlParameterSource()
                .addValue("aliasId", alias.aliasId())
                .addValue("skillId", alias.skillId())
                .addValue("alias", alias.alias())
                .addValue("normalizedAlias", alias.normalizedAlias())
                .addValue("createdAt", Timestamp.from(alias.createdAt()));
    }

    private SkillDictionary mapSkill(ResultSet rs, int rowNum) throws SQLException {
        return new SkillDictionary(
                rs.getString("skill_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("category_id"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
