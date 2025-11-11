package studio.one.base.user.persistence.jdbc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Shared utility base for JDBC-backed repositories.
 */
abstract class BaseJdbcRepository {

    protected final NamedParameterJdbcTemplate namedTemplate;
    protected final JdbcTemplate jdbcTemplate;

    protected BaseJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = namedTemplate.getJdbcTemplate();
    }

    protected MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    protected <T> Page<T> queryPage(
            String selectSql,
            String countSql,
            Map<String, ?> params,
            Pageable pageable,
            RowMapper<T> mapper,
            String defaultSortColumn,
            Map<String, String> propertyToColumn) {

        Map<String, Object> queryParams = new HashMap<>(params == null ? Map.of() : params);
        long total = namedTemplate.queryForObject(countSql, queryParams, Long.class);

        if (total == 0) {
            return Page.empty(pageable);
        }

        String orderBy = buildOrderByClause(pageable.getSort(), defaultSortColumn, propertyToColumn);
        if (pageable.isUnpaged()) {
            var content = namedTemplate.query(selectSql + orderBy, queryParams, mapper);
            return new PageImpl<>(content, pageable, total);
        }

        queryParams.put("limit", pageable.getPageSize());
        queryParams.put("offset", pageable.getOffset());

        String pagedSql = selectSql + orderBy + " limit :limit offset :offset";
        var content = namedTemplate.query(pagedSql, queryParams, mapper);
        return new PageImpl<>(content, pageable, total);
    }

    protected Map<Long, Map<String, String>> fetchProperties(
            String tableName,
            String ownerColumn,
            Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                select %s as owner_id, property_name, property_value
                  from %s
                 where %s in (:ids)
                """.formatted(ownerColumn, tableName, ownerColumn);
        Map<String, Object> params = Map.of("ids", ownerIds);
        Map<Long, Map<String, String>> grouped = new TreeMap<>();
        namedTemplate.query(sql, params, rs -> {
            long ownerId = rs.getLong("owner_id");
            String name = rs.getString("property_name");
            String value = rs.getString("property_value");
            grouped.computeIfAbsent(ownerId, __ -> new HashMap<>()).put(name, value);
        });
        return grouped;
    }

    protected void replaceProperties(
            String tableName,
            String ownerColumn,
            Long ownerId,
            Map<String, String> properties) {
        if (ownerId == null) {
            return;
        }
        namedTemplate.update("delete from %s where %s = :ownerId".formatted(tableName, ownerColumn),
                Map.of("ownerId", ownerId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        String sql = """
                insert into %s (%s, property_name, property_value)
                values (:ownerId, :name, :value)
                """.formatted(tableName, ownerColumn);
        SqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("ownerId", ownerId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(SqlParameterSource[]::new);
        namedTemplate.batchUpdate(sql, batch);
    }

    protected String buildOrderByClause(Sort sort, String defaultColumn, Map<String, String> propertyToColumn) {
        if (sort == null || sort.isUnsorted()) {
            return " order by " + defaultColumn;
        }
        StringBuilder orderBy = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order order : sort) {
            if (!first) {
                orderBy.append(", ");
            }
            first = false;
            String column = resolveColumn(order.getProperty(), propertyToColumn)
                    .orElse(defaultColumn);
            orderBy.append(column).append(order.isAscending() ? " asc" : " desc");
        }
        return orderBy.toString();
    }

    private Optional<String> resolveColumn(String property, Map<String, String> mapping) {
        if (mapping != null && mapping.containsKey(property)) {
            return Optional.of(mapping.get(property));
        }
        if (property == null || property.isBlank()) {
            return Optional.empty();
        }
        String snake = property
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return Optional.of(snake);
    }

    protected <T> Optional<T> queryOptional(String sql, Map<String, ?> params, RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(namedTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    protected <T> Optional<T> queryOptional(String sql, Map<String, ?> params, Class<T> type) {
        try {
            return Optional.ofNullable(namedTemplate.queryForObject(sql, params, type));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }
}
