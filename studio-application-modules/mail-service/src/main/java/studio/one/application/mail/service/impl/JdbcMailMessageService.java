package studio.one.application.mail.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.model.DefaultMailMessage;
import studio.one.application.mail.domain.model.MailMessage;
import studio.one.application.mail.service.MailMessageService;
import studio.one.platform.data.jdbc.PagingJdbcTemplate;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.exception.NotFoundException;

@Transactional
@Service( MailMessageService.SERVICE_NAME)
public class JdbcMailMessageService implements MailMessageService {

    @SqlStatement("data.mail.insert")
    private String insertSql;

    @SqlStatement("data.mail.update")
    private String updateSql;

    @SqlStatement("data.mail.findByUid")
    private String findByUidSql;

    @SqlStatement("data.mail.findByMessageId")
    private String findByMessageIdSql;

    @SqlStatement("data.mail.findById")
    private String findByIdSql;

    @SqlStatement("data.mail.countAll")
    private String countAllSql;

    @SqlStatement("data.mail.findPage")
    private String findPageSql;

    @SqlStatement("data.mail.deleteProperties")
    private String deletePropertiesSql;

    @SqlStatement("data.mail.insertProperty")
    private String insertPropertySql;

    @SqlStatement("data.mail.findProperties")
    private String findPropertiesSql;

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("mailId", "MAIL_ID"),
            Map.entry("folder", "FOLDER"),
            Map.entry("uid", "UID"),
            Map.entry("messageId", "MESSAGE_ID"),
            Map.entry("subject", "SUBJECT"),
            Map.entry("fromAddress", "FROM_ADDRESS"),
            Map.entry("toAddress", "TO_ADDRESS"),
            Map.entry("ccAddress", "CC_ADDRESS"),
            Map.entry("bccAddress", "BCC_ADDRESS"),
            Map.entry("sentAt", "SENT_AT"),
            Map.entry("receivedAt", "RECEIVED_AT"),
            Map.entry("flags", "FLAGS"),
            Map.entry("body", "BODY"),
            Map.entry("createdAt", "CREATED_AT"),
            Map.entry("updatedAt", "UPDATED_AT"));

    private static final RowMapper<MailMessage> ROW_MAPPER = (rs, rowNum) -> {
        DefaultMailMessage message = new DefaultMailMessage();
        message.setMailId(rs.getLong("MAIL_ID"));
        message.setFolder(rs.getString("FOLDER"));
        message.setUid(rs.getLong("UID"));
        message.setMessageId(rs.getString("MESSAGE_ID"));
        message.setSubject(rs.getString("SUBJECT"));
        message.setFromAddress(rs.getString("FROM_ADDRESS"));
        message.setToAddress(rs.getString("TO_ADDRESS"));
        message.setCcAddress(rs.getString("CC_ADDRESS"));
        message.setBccAddress(rs.getString("BCC_ADDRESS"));
        Timestamp sentAt = rs.getTimestamp("SENT_AT");
        Timestamp receivedAt = rs.getTimestamp("RECEIVED_AT");
        message.setSentAt(sentAt != null ? sentAt.toInstant() : null);
        message.setReceivedAt(receivedAt != null ? receivedAt.toInstant() : null);
        message.setFlags(rs.getString("FLAGS"));
        message.setBody(rs.getString("BODY"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        message.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        message.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return message;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PagingJdbcTemplate pagingJdbcTemplate;

    public JdbcMailMessageService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.pagingJdbcTemplate = new PagingJdbcTemplate(jdbcTemplate.getJdbcTemplate().getDataSource());
    }

    @Override
    @Transactional(readOnly = true)
    public MailMessage get(long mailId) {
        MailMessage message = jdbcTemplate.query(findByIdSql, Map.of("mailId", mailId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("mail", mailId));
        message.setProperties(loadProperties(mailId));
        return message;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailMessage> findByFolderAndUid(String folder, long uid) {
        MailMessage message = jdbcTemplate.query(findByUidSql, Map.of("folder", folder, "uid", uid), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElse(null);
        if (message != null) {
            message.setProperties(loadProperties(message.getMailId()));
        }
        return Optional.ofNullable(message);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailMessage> findByMessageId(String messageId) {
        MailMessage message = jdbcTemplate.query(findByMessageIdSql, Map.of("messageId", messageId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElse(null);
        if (message != null) {
            message.setProperties(loadProperties(message.getMailId()));
        }
        return Optional.ofNullable(message);
    }

    @Override
    public MailMessage saveOrUpdate(MailMessage message) {
        if (message.getMailId() <= 0) {
            return insert(message);
        }
        return update(message);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MailMessage> page(Pageable pageable) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        int offset = pageIndex * pageSize;
        Sort sortToUse = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("mailId"));
        String orderedQuery = findPageSql + buildOrderByClause(sortToUse, "mailId", SORT_COLUMNS);
        List<MailMessage> content = pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER);
        long total = jdbcTemplate.queryForObject(countAllSql, Map.of(), Long.class);
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize, sortToUse), total);
    }

    private MailMessage insert(MailMessage message) {
        Instant now = message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("folder", message.getFolder())
                .addValue("uid", message.getUid())
                .addValue("messageId", message.getMessageId())
                .addValue("subject", message.getSubject())
                .addValue("fromAddress", message.getFromAddress())
                .addValue("toAddress", message.getToAddress())
                .addValue("ccAddress", message.getCcAddress())
                .addValue("bccAddress", message.getBccAddress())
                .addValue("sentAt", toTimestamp(message.getSentAt()))
                .addValue("receivedAt", toTimestamp(message.getReceivedAt()))
                .addValue("flags", message.getFlags())
                .addValue("body", message.getBody())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(message.getUpdatedAt() == null ? now : message.getUpdatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, params, keyHolder, new String[] { "MAIL_ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            message.setMailId(key.longValue());
        }
        saveProperties(message.getMailId(), message.getProperties());
        return message;
    }

    private MailMessage update(MailMessage message) {
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mailId", message.getMailId())
                .addValue("folder", message.getFolder())
                .addValue("uid", message.getUid())
                .addValue("messageId", message.getMessageId())
                .addValue("subject", message.getSubject())
                .addValue("fromAddress", message.getFromAddress())
                .addValue("toAddress", message.getToAddress())
                .addValue("ccAddress", message.getCcAddress())
                .addValue("bccAddress", message.getBccAddress())
                .addValue("sentAt", toTimestamp(message.getSentAt()))
                .addValue("receivedAt", toTimestamp(message.getReceivedAt()))
                .addValue("flags", message.getFlags())
                .addValue("body", message.getBody())
                .addValue("updatedAt", Timestamp.from(now));
        jdbcTemplate.update(updateSql, params);
        saveProperties(message.getMailId(), message.getProperties());
        return message;
    }

    private void saveProperties(long mailId, Map<String, String> properties) {
        jdbcTemplate.update(deletePropertiesSql, Map.of("mailId", mailId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("mailId", mailId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(insertPropertySql, batch);
    }

    private Map<String, String> loadProperties(long mailId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(findPropertiesSql, Map.of("mailId", mailId));
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

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
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
                    : toSnakeUpper(defaultProperty);
        }
        String snake = toSnakeUpper(property);
        if (mapping != null && mapping.containsKey(defaultProperty)) {
            return mapping.get(defaultProperty);
        }
        return snake;
    }

    private String toSnakeUpper(String property) {
        return property
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
