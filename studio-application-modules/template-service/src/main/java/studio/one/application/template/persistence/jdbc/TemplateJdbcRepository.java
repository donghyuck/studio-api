package studio.one.application.template.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import studio.one.application.template.domain.model.DefaultTemplate;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.persistence.TemplatePersistenceRepository;
import studio.one.platform.data.jdbc.PagingJdbcTemplate;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;

@Repository
public class TemplateJdbcRepository implements TemplatePersistenceRepository {

    @SqlStatement("data.template.insert")
    private String insertSql;

    @SqlStatement("data.template.update")
    private String updateSql;

    @SqlStatement("data.template.findById")
    private String findByIdSql;

    @SqlStatement("data.template.findByName")
    private String findByNameSql;

    @SqlStatement("data.template.delete")
    private String deleteSql;

    @SqlStatement("data.template.deleteProperties")
    private String deletePropertiesSql;

    @SqlStatement("data.template.insertProperty")
    private String insertPropertySql;

    @SqlStatement("data.template.findProperties")
    private String findPropertiesSql;

    @SqlStatement("data.template.countAll")
    private String countAllSql;

    @SqlStatement("data.template.findPage")
    private String findPageSql;

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("templateId", "TEMPLATE_ID"),
            Map.entry("objectType", "OBJECT_TYPE"),
            Map.entry("objectId", "OBJECT_ID"),
            Map.entry("name", "NAME"),
            Map.entry("displayName", "DISPLAY_NAME"),
            Map.entry("description", "DESCRIPTION"),
            Map.entry("subject", "SUBJECT"),
            Map.entry("createdBy", "CREATED_BY"),
            Map.entry("updatedBy", "UPDATED_BY"),
            Map.entry("createdAt", "CREATED_AT"),
            Map.entry("updatedAt", "UPDATED_AT"));

    private static final RowMapper<Template> ROW_MAPPER = (rs, rowNum) -> {
        DefaultTemplate template = new DefaultTemplate();
        template.setTemplateId(rs.getLong("TEMPLATE_ID"));
        template.setObjectType(rs.getInt("OBJECT_TYPE"));
        template.setObjectId(rs.getLong("OBJECT_ID"));
        template.setName(rs.getString("NAME"));
        template.setDisplayName(rs.getString("DISPLAY_NAME"));
        template.setDescription(rs.getString("DESCRIPTION"));
        template.setSubject(rs.getString("SUBJECT"));
        template.setBody(rs.getString("BODY"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        template.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        template.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        template.setCreatedBy(rs.getLong("CREATED_BY"));
        template.setUpdatedBy(rs.getLong("UPDATED_BY"));
        return template;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PagingJdbcTemplate pagingJdbcTemplate;

    public TemplateJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        DataSource dataSource = jdbcTemplate.getJdbcTemplate().getDataSource();
        this.pagingJdbcTemplate = dataSource != null ? new PagingJdbcTemplate(dataSource) : null;
    }

    @Override
    public Optional<Template> findByName(String name) {
        Template template = jdbcTemplate.query(findByNameSql, Map.of("name", name), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElse(null);
        if (template != null) {
            template.setProperties(loadProperties(template.getTemplateId()));
        }
        return Optional.ofNullable(template);
    }

    @Override
    public Optional<Template> findById(long templateId) {
        Template template = jdbcTemplate.query(findByIdSql, Map.of("templateId", templateId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElse(null);
        if (template != null) {
            template.setProperties(loadProperties(templateId));
        }
        return Optional.ofNullable(template);
    }

    @Override
    public Template save(Template template) {
        if (template.getTemplateId() <= 0) {
            return insert(template);
        }
        return update(template);
    }

    @Override
    public void deleteById(long templateId) {
        Map<String, Object> params = Map.of("templateId", templateId);
        jdbcTemplate.update(deletePropertiesSql, params);
        jdbcTemplate.update(deleteSql, params);
    }

    @Override
    public Page<Template> page(Pageable pageable) {
        return page(pageable, null, null);
    }

    @Override
    public Page<Template> page(Pageable pageable, String query, String fields) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        int offset = pageIndex * pageSize;
        Sort sortToUse = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("templateId"));
        boolean hasQuery = StringUtils.hasText(query);
        Set<String> resolvedFields = resolveFields(fields);
        String whereClause = hasQuery ? buildWhereClause(resolvedFields) : "";
        String orderedQuery = findPageSql + whereClause + buildOrderByClause(sortToUse, "templateId", SORT_COLUMNS);
        Object[] args = buildKeywordArgs(query, resolvedFields);
        List<Template> content;
        if (pagingJdbcTemplate != null) {
            content = hasQuery
                    ? pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER, args)
                    : pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER);
        } else {
            String paged = orderedQuery + " limit " + pageSize + " offset " + offset;
            content = hasQuery
                    ? jdbcTemplate.getJdbcTemplate().query(paged, ROW_MAPPER, args)
                    : jdbcTemplate.getJdbcTemplate().query(paged, ROW_MAPPER);
        }
        long total = hasQuery
                ? jdbcTemplate.getJdbcTemplate().queryForObject(countAllSql + whereClause, args, Long.class)
                : jdbcTemplate.queryForObject(countAllSql, Map.of(), Long.class);
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize, sortToUse), total);
    }

    private Template insert(Template template) {
        Instant now = template.getCreatedAt() == null ? Instant.now() : template.getCreatedAt();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", template.getObjectType())
                .addValue("objectId", template.getObjectId())
                .addValue("name", template.getName())
                .addValue("displayName", template.getDisplayName())
                .addValue("description", template.getDescription())
                .addValue("subject", template.getSubject())
                .addValue("body", template.getBody())
                .addValue("createdBy", template.getCreatedBy())
                .addValue("updatedBy", template.getUpdatedBy())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(template.getUpdatedAt() == null ? now : template.getUpdatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, params, keyHolder, new String[] { "TEMPLATE_ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            template.setTemplateId(key.longValue());
        }
        saveProperties(template.getTemplateId(), template.getProperties());
        return template;
    }

    private Template update(Template template) {
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("templateId", template.getTemplateId())
                .addValue("objectType", template.getObjectType())
                .addValue("objectId", template.getObjectId())
                .addValue("name", template.getName())
                .addValue("displayName", template.getDisplayName())
                .addValue("description", template.getDescription())
                .addValue("subject", template.getSubject())
                .addValue("body", template.getBody())
                .addValue("updatedBy", template.getUpdatedBy())
                .addValue("updatedAt", Timestamp.from(now));
        jdbcTemplate.update(updateSql, params);
        saveProperties(template.getTemplateId(), template.getProperties());
        return template;
    }

    private void saveProperties(long templateId, Map<String, String> properties) {
        jdbcTemplate.update(deletePropertiesSql, Map.of("templateId", templateId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("templateId", templateId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(insertPropertySql, batch);
    }

    private Map<String, String> loadProperties(long templateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(findPropertiesSql, Map.of("templateId", templateId));
        Map<String, String> props = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("PROPERTY_NAME");
            Object value = row.get("PROPERTY_VALUE");
            if (name != null) {
                props.put(name.toString(), value != null ? value.toString() : null);
            }
        }
        return props;
    }

    private String buildOrderByClause(Sort sort, String defaultProperty, Map<String, String> propertyToColumn) {
        Sort sortToUse = (sort == null || sort.isUnsorted())
                ? Sort.by(Sort.Order.desc(defaultProperty))
                : sort;
        StringBuilder orderBy = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order order : sortToUse) {
            if (!first) {
                orderBy.append(", ");
            }
            first = false;
            String column = resolveColumn(order.getProperty(), propertyToColumn, defaultProperty);
            orderBy.append(column).append(order.isAscending() ? " asc" : " desc");
        }
        return orderBy.toString();
    }

    private String resolveColumn(String property, Map<String, String> mapping, String defaultProperty) {
        if (property != null && mapping != null && mapping.containsKey(property)) {
            return mapping.get(property);
        }
        if (property == null || property.isBlank()) {
            return mapping != null && mapping.containsKey(defaultProperty)
                    ? mapping.get(defaultProperty)
                    : defaultProperty;
        }
        return mapping != null && mapping.containsKey(defaultProperty)
                ? mapping.get(defaultProperty)
                : defaultProperty;
    }

    private Object[] buildKeywordArgs(String query, Set<String> fields) {
        if (!StringUtils.hasText(query)) {
            return new Object[0];
        }
        String needle = "%" + query.trim().toLowerCase() + "%";
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            args.add(needle);
        }
        return args.toArray();
    }

    private String buildWhereClause(Set<String> fields) {
        if (fields.isEmpty()) {
            return "";
        }
        StringBuilder where = new StringBuilder(" where ");
        boolean first = true;
        for (String field : fields) {
            String column = resolveColumn(field, FIELD_COLUMNS, field);
            if (!first) {
                where.append(" or ");
            }
            first = false;
            where.append("lower(coalesce(").append(column).append(", '')) like ?");
        }
        return where.toString();
    }

    private Set<String> resolveFields(String fields) {
        Map<String, String> allowed = new LinkedHashMap<>(FIELD_COLUMNS);
        if (!StringUtils.hasText(fields)) {
            return allowed.keySet();
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : fields.split(",")) {
            String field = raw.trim();
            if (allowed.containsKey(field)) {
                selected.add(field);
            }
        }
        return selected.isEmpty() ? allowed.keySet() : selected;
    }

    private static final Map<String, String> FIELD_COLUMNS = Map.ofEntries(
            Map.entry("name", "NAME"),
            Map.entry("displayName", "DISPLAY_NAME"),
            Map.entry("description", "DESCRIPTION"),
            Map.entry("subject", "SUBJECT"),
            Map.entry("body", "BODY"));
}
