package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
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
    public SkillDictionary saveEmbedding(String skillId, List<Double> embedding, String embeddingModel) {
        SkillDictionary skill = findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        if (embedding == null || embedding.isEmpty()) {
            return skill;
        }
        template.update("""
                UPDATE tb_skill_dictionary
                SET embedding = CAST(:embedding AS vector),
                    updated_at = :updatedAt
                WHERE skill_id = :skillId
                """, new MapSqlParameterSource()
                .addValue("skillId", skillId)
                .addValue("embedding", vectorLiteral(embedding))
                .addValue("updatedAt", Timestamp.from(Instant.now())));
        return findById(skillId).orElse(skill);
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
    public Page<SkillDictionary> search(String q, Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase();
        int limit = pageable.getPageSize();
        long offset = pageable.getOffset();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", query)
                .addValue("likeQ", "%" + query + "%")
                .addValue("limit", limit)
                .addValue("offset", offset);

        String whereSql = """
                WHERE (:q = '' OR LOWER(name) LIKE :likeQ OR normalized_name LIKE :likeQ)
                """;

        List<SkillDictionary> content = template.query("""
                SELECT * FROM tb_skill_dictionary
                """ + whereSql + """
                ORDER BY name
                LIMIT :limit OFFSET :offset
                """, params, this::mapSkill);

        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary
                """ + whereSql,
                params,
                Long.class);

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillDictionary> findByCategoryId(String categoryId, int limit) {
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT * FROM tb_skill_dictionary
                WHERE category_id = :categoryId
                ORDER BY name
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("limit", max), this::mapSkill);
    }

    @Override
    public int countByCategoryId(String categoryId) {
        Integer count = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary
                WHERE category_id = :categoryId
                """, Map.of("categoryId", categoryId), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<SkillVectorItem> findVectorItems(int limit) {
        int max = limit <= 0 ? 1000 : limit;
        return template.query("""
                SELECT skill_id, name, embedding::text AS embedding_text, created_at
                FROM tb_skill_dictionary
                WHERE status = 'ACTIVE' AND embedding IS NOT NULL
                ORDER BY name
                LIMIT :limit
                """, Map.of("limit", max), (rs, rowNum) -> new SkillVectorItem(
                rs.getString("skill_id"),
                rs.getString("name"),
                parseVector(rs.getString("embedding_text")),
                null,
                instant(rs.getTimestamp("created_at"))));
    }

    @Override
    public List<SkillDictionary> findMissingEmbeddingSkills(int limit) {
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT * FROM tb_skill_dictionary
                WHERE status = 'ACTIVE' AND embedding IS NULL
                ORDER BY name
                LIMIT :limit
                """, Map.of("limit", max), this::mapSkill);
    }

    @Override
    public int countMissingEmbeddingSkills() {
        Integer count = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary
                WHERE status = 'ACTIVE' AND embedding IS NULL
                """, Map.of(), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int updateCategory(List<String> skillIds, String categoryId) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET category_id = :categoryId,
                    updated_at = :updatedAt
                WHERE skill_id IN (:skillIds)
                """, new MapSqlParameterSource()
                .addValue("skillIds", skillIds)
                .addValue("categoryId", categoryId)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    @Override
    public int updateCategoryByCategoryIds(List<String> sourceCategoryIds, String targetCategoryId) {
        if (sourceCategoryIds == null || sourceCategoryIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET category_id = :targetCategoryId,
                    updated_at = :updatedAt
                WHERE category_id IN (:sourceCategoryIds)
                """, new MapSqlParameterSource()
                .addValue("sourceCategoryIds", sourceCategoryIds)
                .addValue("targetCategoryId", targetCategoryId)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    @Override
    public int updateStatus(List<String> skillIds, String status) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET status = :status,
                    updated_at = :updatedAt
                WHERE skill_id IN (:skillIds)
                """, new MapSqlParameterSource()
                .addValue("skillIds", skillIds)
                .addValue("status", status)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
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

    private String vectorLiteral(List<Double> embedding) {
        return embedding.toString();
    }

    private List<Double> parseVector(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String stripped = value.trim()
                .replace("[", "")
                .replace("]", "");
        if (stripped.isBlank()) {
            return List.of();
        }
        String[] parts = stripped.split(",");
        List<Double> vector = new ArrayList<>(parts.length);
        for (String part : parts) {
            vector.add(Double.parseDouble(part.trim()));
        }
        return vector;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
