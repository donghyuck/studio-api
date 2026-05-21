package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

@RequiredArgsConstructor
public class JdbcSkillTaxonomyStore implements SkillTaxonomyStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillCategory saveCategory(SkillCategory category) {
        int updated = template.update("""
                UPDATE tb_skill_category
                SET parent_category_id = :parentCategoryId,
                    name = :name,
                    display_order = :displayOrder
                WHERE category_id = :categoryId
                """, params(category));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_category(category_id, parent_category_id, name, display_order)
                    VALUES(:categoryId, :parentCategoryId, :name, :displayOrder)
                    """, params(category));
        }
        return category;
    }

    @Override
    public Optional<SkillCategory> findCategory(String categoryId) {
        return template.query("""
                SELECT * FROM tb_skill_category WHERE category_id = :categoryId
                """, Map.of("categoryId", categoryId), this::map).stream().findFirst();
    }

    @Override
    public List<SkillCategory> findCategories(String parentCategoryId) {
        String parent = normalize(parentCategoryId);
        if (parent == null) {
            return template.query("""
                    SELECT * FROM tb_skill_category
                    WHERE parent_category_id IS NULL
                    ORDER BY display_order, name
                    """, Map.of(), this::map);
        }
        return template.query("""
                SELECT * FROM tb_skill_category
                WHERE parent_category_id = :parentCategoryId
                ORDER BY display_order, name
                """, new MapSqlParameterSource("parentCategoryId", parent), this::map);
    }

    @Override
    public Page<SkillCategory> searchCategories(String q, String parentCategoryId, Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase();
        String parent = normalize(parentCategoryId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", query)
                .addValue("likeQ", "%" + query + "%")
                .addValue("parentCategoryId", parent)
                .addValue("parentIsNull", parent == null)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        String whereSql = """
                WHERE (:parentIsNull = TRUE OR parent_category_id = :parentCategoryId)
                  AND (:q = '' OR LOWER(name) LIKE :likeQ OR LOWER(category_id) LIKE :likeQ)
                """;
        List<SkillCategory> content = template.query("""
                SELECT * FROM tb_skill_category
                """ + whereSql + """
                ORDER BY display_order, name
                LIMIT :limit OFFSET :offset
                """, params, this::map);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_category
                """ + whereSql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public void deleteCategory(String categoryId) {
        template.update("DELETE FROM tb_skill_category WHERE category_id = :categoryId",
                Map.of("categoryId", categoryId));
    }

    @Override
    public List<SkillCategory> findDescendants(String categoryId) {
        List<SkillCategory> children = findCategories(categoryId);
        return children.stream()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child),
                        findDescendants(child.categoryId()).stream()))
                .toList();
    }

    @Override
    public SkillCategoryHistory saveHistory(SkillCategoryHistory history) {
        template.update("""
                INSERT INTO tb_skill_category_history
                    (history_id, category_id, skill_id, action_type, previous_category_id, new_category_id, detail, created_at)
                VALUES
                    (:historyId, :categoryId, :skillId, :actionType, :previousCategoryId, :newCategoryId, :detail, :createdAt)
                """, historyParams(history));
        return history;
    }

    @Override
    public Page<SkillCategoryHistory> findCategoryHistory(String categoryId, Pageable pageable) {
        return historyPage("""
                WHERE category_id = :categoryId
                """, new MapSqlParameterSource("categoryId", categoryId), pageable);
    }

    @Override
    public Page<SkillCategoryHistory> findSkillCategoryHistory(String skillId, Pageable pageable) {
        return historyPage("""
                WHERE skill_id = :skillId
                """, new MapSqlParameterSource("skillId", skillId), pageable);
    }

    private MapSqlParameterSource params(SkillCategory category) {
        return new MapSqlParameterSource()
                .addValue("categoryId", category.categoryId())
                .addValue("parentCategoryId", category.parentCategoryId())
                .addValue("name", category.name())
                .addValue("displayOrder", category.displayOrder());
    }

    private SkillCategory map(ResultSet rs, int rowNum) throws SQLException {
        return new SkillCategory(
                rs.getString("category_id"),
                rs.getString("parent_category_id"),
                rs.getString("name"),
                rs.getInt("display_order"));
    }

    private Page<SkillCategoryHistory> historyPage(
            String whereSql,
            MapSqlParameterSource params,
            Pageable pageable) {
        params.addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<SkillCategoryHistory> content = template.query("""
                SELECT * FROM tb_skill_category_history
                """ + whereSql + """
                ORDER BY created_at DESC, history_id DESC
                LIMIT :limit OFFSET :offset
                """, params, this::mapHistory);
        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_category_history
                """ + whereSql,
                params,
                Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private MapSqlParameterSource historyParams(SkillCategoryHistory history) {
        return new MapSqlParameterSource()
                .addValue("historyId", history.historyId())
                .addValue("categoryId", history.categoryId())
                .addValue("skillId", history.skillId())
                .addValue("actionType", history.actionType())
                .addValue("previousCategoryId", history.previousCategoryId())
                .addValue("newCategoryId", history.newCategoryId())
                .addValue("detail", history.detail())
                .addValue("createdAt", Timestamp.from(history.createdAt()));
    }

    private SkillCategoryHistory mapHistory(ResultSet rs, int rowNum) throws SQLException {
        return new SkillCategoryHistory(
                rs.getString("history_id"),
                rs.getString("category_id"),
                rs.getString("skill_id"),
                rs.getString("action_type"),
                rs.getString("previous_category_id"),
                rs.getString("new_category_id"),
                rs.getString("detail"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
