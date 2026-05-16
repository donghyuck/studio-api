package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
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
        return template.query("""
                SELECT * FROM tb_skill_category
                WHERE (:parentCategoryId IS NULL AND parent_category_id IS NULL)
                   OR parent_category_id = :parentCategoryId
                ORDER BY display_order, name
                """, new MapSqlParameterSource("parentCategoryId", normalize(parentCategoryId)), this::map);
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
