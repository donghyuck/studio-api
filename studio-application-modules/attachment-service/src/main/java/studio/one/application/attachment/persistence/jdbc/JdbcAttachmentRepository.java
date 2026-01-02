package studio.one.application.attachment.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.persistence.AttachmentRepository;

@Repository
public class JdbcAttachmentRepository implements AttachmentRepository {

    private static final String TABLE = "TB_APPLICATION_ATTACHMENT";
    private static final String PROPERTY_TABLE = "TB_APPLICATION_ATTACHMENT_PROPERTY";

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "attachmentId", "ATTACHMENT_ID",
            "objectType", "OBJECT_TYPE",
            "objectId", "OBJECT_ID",
            "name", "FILE_NAME",
            "contentType", "CONTENT_TYPE",
            "size", "FILE_SIZE",
            "createdBy", "CREATED_BY",
            "createdAt", "CREATED_AT",
            "updatedAt", "UPDATED_AT");

    private static final RowMapper<ApplicationAttachment> ATTACHMENT_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationAttachment attachment = new ApplicationAttachment();
        attachment.setAttachmentId(rs.getLong("ATTACHMENT_ID"));
        attachment.setObjectType(rs.getInt("OBJECT_TYPE"));
        attachment.setObjectId(rs.getLong("OBJECT_ID"));
        attachment.setContentType(rs.getString("CONTENT_TYPE"));
        attachment.setName(rs.getString("FILE_NAME"));
        attachment.setSize(rs.getInt("FILE_SIZE"));
        attachment.setCreatedBy(rs.getLong("CREATED_BY"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        attachment.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        attachment.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
        attachment.setProperties(new HashMap<>());
        return attachment;
    };

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert insert;

    public JdbcAttachmentRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = namedTemplate.getJdbcTemplate();
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate)
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("ATTACHMENT_ID");
    }

    @Override
    public ApplicationAttachment save(ApplicationAttachment attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("attachment must not be null");
        }
        if (attachment.getAttachmentId() <= 0) {
            return insert(attachment);
        }
        return update(attachment);
    }

    @Override
    public Optional<ApplicationAttachment> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where ATTACHMENT_ID = :id
                """.formatted(TABLE);
        Optional<ApplicationAttachment> result = queryOptional(sql, Map.of("id", id), ATTACHMENT_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public void delete(ApplicationAttachment attachment) {
        if (attachment == null || attachment.getAttachmentId() <= 0) {
            return;
        }
        Map<String, Object> params = Map.of("id", attachment.getAttachmentId());
        namedTemplate.update("delete from %s where ATTACHMENT_ID = :id".formatted(PROPERTY_TABLE), params);
        namedTemplate.update("delete from %s where ATTACHMENT_ID = :id".formatted(TABLE), params);
    }

    @Override
    public List<ApplicationAttachment> findByObjectTypeAndObjectId(int objectType, Long objectId) {
        String sql = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId
                 order by ATTACHMENT_ID
                """.formatted(TABLE);
        Map<String, Object> params = Map.of("objectType", objectType, "objectId", objectId);
        List<ApplicationAttachment> attachments = namedTemplate.query(sql, params, ATTACHMENT_ROW_MAPPER);
        loadProperties(attachments);
        return attachments;
    }

    @Override
    public Page<ApplicationAttachment> findByObjectTypeAndObjectId(int objectType, Long objectId, Pageable pageable) {
        Map<String, Object> params = Map.of("objectType", objectType, "objectId", objectId);
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId
                """.formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public List<ApplicationAttachment> findByObjectTypeAndObjectIdAndCreatedBy(int objectType, Long objectId,
            long createdBy) {
        String sql = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId and CREATED_BY = :createdBy
                 order by ATTACHMENT_ID
                """.formatted(TABLE);
        Map<String, Object> params = Map.of("objectType", objectType, "objectId", objectId, "createdBy", createdBy);
        List<ApplicationAttachment> attachments = namedTemplate.query(sql, params, ATTACHMENT_ROW_MAPPER);
        loadProperties(attachments);
        return attachments;
    }

    @Override
    public Page<ApplicationAttachment> findByObjectTypeAndObjectIdAndCreatedBy(int objectType, Long objectId,
            long createdBy, Pageable pageable) {
        Map<String, Object> params = Map.of("objectType", objectType, "objectId", objectId, "createdBy", createdBy);
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId and CREATED_BY = :createdBy
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId and CREATED_BY = :createdBy
                """.formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findAll(Pageable pageable) {
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                """.formatted(TABLE);
        String count = "select count(*) from %s".formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, Map.of(), pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findByNameContainingIgnoreCase(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findByObjectTypeAndObjectIdAndNameContainingIgnoreCase(
            int objectType, Long objectId, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findByObjectTypeAndObjectId(objectType, objectId, pageable);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("objectType", objectType);
        params.put("objectId", objectId);
        params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findByCreatedBy(long createdBy, Pageable pageable) {
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where CREATED_BY = :createdBy
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where CREATED_BY = :createdBy
                """.formatted(TABLE);
        Map<String, Object> params = Map.of("createdBy", createdBy);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findByCreatedByAndNameContainingIgnoreCase(
            long createdBy, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findByCreatedBy(createdBy, pageable);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("createdBy", createdBy);
        params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where CREATED_BY = :createdBy
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where CREATED_BY = :createdBy
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationAttachment> findByObjectTypeAndObjectIdAndCreatedByAndNameContainingIgnoreCase(
            int objectType, Long objectId, long createdBy, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findByObjectTypeAndObjectIdAndCreatedBy(objectType, objectId, createdBy, pageable);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("objectType", objectType);
        params.put("objectId", objectId);
        params.put("createdBy", createdBy);
        params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        String select = """
                select ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, CONTENT_TYPE, FILE_NAME, FILE_SIZE,
                       CREATED_BY, CREATED_AT, UPDATED_AT
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId and CREATED_BY = :createdBy
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where OBJECT_TYPE = :objectType and OBJECT_ID = :objectId and CREATED_BY = :createdBy
                   and lower(FILE_NAME) like :keyword
                """.formatted(TABLE);
        Page<ApplicationAttachment> page = queryPage(select, count, params, pageable, ATTACHMENT_ROW_MAPPER);
        loadProperties(page.getContent());
        return page;
    }

    private ApplicationAttachment insert(ApplicationAttachment attachment) {
        Instant now = Instant.now();
        if (attachment.getCreatedAt() == null) {
            attachment.setCreatedAt(now);
        }
        if (attachment.getUpdatedAt() == null) {
            attachment.setUpdatedAt(attachment.getCreatedAt());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("OBJECT_TYPE", attachment.getObjectType())
                .addValue("OBJECT_ID", attachment.getObjectId())
                .addValue("CONTENT_TYPE", attachment.getContentType())
                .addValue("FILE_NAME", attachment.getName())
                .addValue("FILE_SIZE", attachment.getSize())
                .addValue("CREATED_BY", attachment.getCreatedBy())
                .addValue("CREATED_AT", Timestamp.from(attachment.getCreatedAt()))
                .addValue("UPDATED_AT", Timestamp.from(attachment.getUpdatedAt()));
        Number key = insert.executeAndReturnKey(params);
        attachment.setAttachmentId(key.longValue());
        replaceProperties(attachment.getAttachmentId(), attachment.getProperties());
        return attachment;
    }

    private ApplicationAttachment update(ApplicationAttachment attachment) {
        Instant updatedAt = attachment.getUpdatedAt();
        if (updatedAt == null) {
            updatedAt = Instant.now();
            attachment.setUpdatedAt(updatedAt);
        }
        String sql = """
                update %s
                   set OBJECT_TYPE = :objectType,
                       OBJECT_ID = :objectId,
                       CONTENT_TYPE = :contentType,
                       FILE_NAME = :fileName,
                       FILE_SIZE = :fileSize,
                       UPDATED_AT = :updatedAt
                 where ATTACHMENT_ID = :id
                """.formatted(TABLE);
        Map<String, Object> params = new HashMap<>();
        params.put("objectType", attachment.getObjectType());
        params.put("objectId", attachment.getObjectId());
        params.put("contentType", attachment.getContentType());
        params.put("fileName", attachment.getName());
        params.put("fileSize", attachment.getSize());
        params.put("updatedAt", Timestamp.from(updatedAt));
        params.put("id", attachment.getAttachmentId());
        namedTemplate.update(sql, params);
        replaceProperties(attachment.getAttachmentId(), attachment.getProperties());
        return attachment;
    }

    private Page<ApplicationAttachment> queryPage(
            String selectSql,
            String countSql,
            Map<String, ?> params,
            Pageable pageable,
            RowMapper<ApplicationAttachment> mapper) {

        Map<String, Object> queryParams = new HashMap<>(params == null ? Map.of() : params);
        long total = namedTemplate.queryForObject(countSql, queryParams, Long.class);
        if (total == 0) {
            return Page.empty(pageable);
        }

        String orderBy = buildOrderByClause(pageable == null ? null : pageable.getSort(), "ATTACHMENT_ID", SORT_COLUMNS);
        if (pageable == null || pageable.isUnpaged()) {
            List<ApplicationAttachment> content = namedTemplate.query(selectSql + orderBy, queryParams, mapper);
            return new PageImpl<>(content, pageable == null ? Pageable.unpaged() : pageable, total);
        }

        queryParams.put("limit", pageable.getPageSize());
        queryParams.put("offset", pageable.getOffset());

        String pagedSql = selectSql + orderBy + " limit :limit offset :offset";
        List<ApplicationAttachment> content = namedTemplate.query(pagedSql, queryParams, mapper);
        return new PageImpl<>(content, pageable, total);
    }

    private Optional<ApplicationAttachment> queryOptional(
            String sql,
            Map<String, ?> params,
            RowMapper<ApplicationAttachment> mapper) {
        try {
            return Optional.ofNullable(namedTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private void loadProperties(ApplicationAttachment attachment) {
        if (attachment == null || attachment.getAttachmentId() <= 0) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(List.of(attachment.getAttachmentId()));
        Map<String, String> map = props.get(attachment.getAttachmentId());
        attachment.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
    }

    private void loadProperties(List<ApplicationAttachment> attachments) {
        List<Long> ids = attachments.stream()
                .map(ApplicationAttachment::getAttachmentId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(ids);
        for (ApplicationAttachment attachment : attachments) {
            Map<String, String> map = props.get(attachment.getAttachmentId());
            attachment.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
        }
    }

    private Map<Long, Map<String, String>> fetchProperties(Collection<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                select ATTACHMENT_ID, PROPERTY_NAME, PROPERTY_VALUE
                  from %s
                 where ATTACHMENT_ID in (:ids)
                """.formatted(PROPERTY_TABLE);
        Map<Long, Map<String, String>> grouped = new TreeMap<>();
        namedTemplate.query(sql, Map.of("ids", attachmentIds), rs -> {
            long id = rs.getLong("ATTACHMENT_ID");
            String name = rs.getString("PROPERTY_NAME");
            String value = rs.getString("PROPERTY_VALUE");
            grouped.computeIfAbsent(id, __ -> new HashMap<>()).put(name, value);
        });
        return grouped;
    }

    private void replaceProperties(Long attachmentId, Map<String, String> properties) {
        if (attachmentId == null || attachmentId <= 0) {
            return;
        }
        namedTemplate.update(
                "delete from %s where ATTACHMENT_ID = :id".formatted(PROPERTY_TABLE),
                Map.of("id", attachmentId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        String sql = """
                insert into %s (ATTACHMENT_ID, PROPERTY_NAME, PROPERTY_VALUE)
                values (:id, :name, :value)
                """.formatted(PROPERTY_TABLE);
        SqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("id", attachmentId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(SqlParameterSource[]::new);
        namedTemplate.batchUpdate(sql, batch);
    }

    private String buildOrderByClause(
            Sort sort,
            String defaultColumn,
            Map<String, String> propertyToColumn) {
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
}
