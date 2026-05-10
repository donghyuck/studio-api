package studio.one.application.mail.application.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.springframework.util.StringUtils;

import studio.one.application.mail.domain.model.DefaultMailMessage;
import studio.one.application.mail.domain.model.MailMessage;
import studio.one.application.mail.application.usecase.MailMessageService;
import studio.one.platform.data.jdbc.PagingJdbcTemplate;
import studio.one.platform.exception.NotFoundException;

@Transactional
@Service( MailMessageService.SERVICE_NAME)
public class JdbcMailMessageService implements MailMessageService {

    private static final String INSERT_SQL = """
            insert into TB_APPLICATION_MAIL_MESSAGE
                (FOLDER, UID, MESSAGE_ID, SUBJECT, FROM_ADDRESS, TO_ADDRESS, CC_ADDRESS, BCC_ADDRESS,
                 SENT_AT, RECEIVED_AT, FLAGS, BODY, CREATED_AT, UPDATED_AT)
            values
                (:folder, :uid, :messageId, :subject, :fromAddress, :toAddress, :ccAddress, :bccAddress,
                 :sentAt, :receivedAt, :flags, :body, :createdAt, :updatedAt)
            returning MAIL_ID
            """;

    private static final String UPDATE_SQL = """
            update TB_APPLICATION_MAIL_MESSAGE
               set FOLDER       = :folder,
                   UID          = :uid,
                   MESSAGE_ID   = :messageId,
                   SUBJECT      = :subject,
                   FROM_ADDRESS = :fromAddress,
                   TO_ADDRESS   = :toAddress,
                   CC_ADDRESS   = :ccAddress,
                   BCC_ADDRESS  = :bccAddress,
                   SENT_AT      = :sentAt,
                   RECEIVED_AT  = :receivedAt,
                   FLAGS        = :flags,
                   BODY         = :body,
                   UPDATED_AT   = :updatedAt
             where MAIL_ID      = :mailId
            """;

    private static final String FIND_BY_UID_SQL = """
            select MAIL_ID, FOLDER, UID, MESSAGE_ID, SUBJECT, FROM_ADDRESS, TO_ADDRESS, CC_ADDRESS, BCC_ADDRESS,
                   SENT_AT, RECEIVED_AT, FLAGS, BODY, CREATED_AT, UPDATED_AT
              from TB_APPLICATION_MAIL_MESSAGE
             where FOLDER = :folder
               and UID = :uid
            """;

    private static final String FIND_BY_MESSAGE_ID_SQL = """
            select MAIL_ID, FOLDER, UID, MESSAGE_ID, SUBJECT, FROM_ADDRESS, TO_ADDRESS, CC_ADDRESS, BCC_ADDRESS,
                   SENT_AT, RECEIVED_AT, FLAGS, BODY, CREATED_AT, UPDATED_AT
              from TB_APPLICATION_MAIL_MESSAGE
             where MESSAGE_ID = :messageId
            """;

    private static final String FIND_BY_ID_SQL = """
            select MAIL_ID, FOLDER, UID, MESSAGE_ID, SUBJECT, FROM_ADDRESS, TO_ADDRESS, CC_ADDRESS, BCC_ADDRESS,
                   SENT_AT, RECEIVED_AT, FLAGS, BODY, CREATED_AT, UPDATED_AT
              from TB_APPLICATION_MAIL_MESSAGE
             where MAIL_ID = :mailId
            """;

    private static final String COUNT_ALL_SQL = "select count(*) from TB_APPLICATION_MAIL_MESSAGE";

    private static final String FIND_PAGE_SQL = """
            select MAIL_ID, FOLDER, UID, MESSAGE_ID, SUBJECT, FROM_ADDRESS, TO_ADDRESS, CC_ADDRESS, BCC_ADDRESS,
                   SENT_AT, RECEIVED_AT, FLAGS, BODY, CREATED_AT, UPDATED_AT
              from TB_APPLICATION_MAIL_MESSAGE
            """;

    private static final String DELETE_PROPERTIES_SQL = """
            delete from TB_APPLICATION_MAIL_PROPERTY
             where MAIL_ID = :mailId
            """;

    private static final String INSERT_PROPERTY_SQL = """
            insert into TB_APPLICATION_MAIL_PROPERTY
                (MAIL_ID, PROPERTY_NAME, PROPERTY_VALUE)
            values
                (:mailId, :name, :value)
            """;

    private static final String FIND_PROPERTIES_SQL = """
            select PROPERTY_NAME, PROPERTY_VALUE
              from TB_APPLICATION_MAIL_PROPERTY
             where MAIL_ID = :mailId
            """;

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
        MailMessage message = jdbcTemplate.query(FIND_BY_ID_SQL, Map.of("mailId", mailId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("mail", mailId));
        message.setProperties(loadProperties(mailId));
        return message;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailMessage> findByFolderAndUid(String folder, long uid) {
        MailMessage message = jdbcTemplate.query(FIND_BY_UID_SQL, Map.of("folder", folder, "uid", uid), ROW_MAPPER)
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
        MailMessage message = jdbcTemplate.query(FIND_BY_MESSAGE_ID_SQL, Map.of("messageId", messageId), ROW_MAPPER)
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
        return page(pageable, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MailMessage> page(Pageable pageable, String query, String fields) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        int offset = pageIndex * pageSize;
        Sort sortToUse = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("mailId"));
        boolean hasQuery = StringUtils.hasText(query);
        Set<String> resolvedFields = resolveFields(fields);
        String whereClause = hasQuery ? buildWhereClause(resolvedFields) : "";
        String orderedQuery = FIND_PAGE_SQL + whereClause + buildOrderByClause(sortToUse, "mailId", SORT_COLUMNS);
        Object[] args = buildKeywordArgs(query, resolvedFields);
        List<MailMessage> content = hasQuery
                ? pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER, args)
                : pagingJdbcTemplate.queryPage(orderedQuery, offset, pageSize, ROW_MAPPER);
        long total = hasQuery
                ? jdbcTemplate.getJdbcTemplate().queryForObject(COUNT_ALL_SQL + whereClause, args, Long.class)
                : jdbcTemplate.queryForObject(COUNT_ALL_SQL, Map.of(), Long.class);
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize, sortToUse), total);
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
            String column = resolveColumn(field, SORT_COLUMNS, field);
            if (!first) {
                where.append(" or ");
            }
            first = false;
            where.append("lower(coalesce(").append(column).append(", '')) like ?");
        }
        return where.toString();
    }

    private Set<String> resolveFields(String fields) {
        Map<String, String> allowed = new LinkedHashMap<>(SORT_COLUMNS);
        allowed.put("body", "BODY");
        Set<String> selected = new LinkedHashSet<>();
        if (StringUtils.hasText(fields)) {
            for (String raw : fields.split(",")) {
                String field = raw.trim();
                if (allowed.containsKey(field)) {
                    selected.add(field);
                }
            }
        }
        return selected.isEmpty() ? allowed.keySet() : selected;
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
        jdbcTemplate.update(INSERT_SQL, params, keyHolder, new String[] { "MAIL_ID" });
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
        jdbcTemplate.update(UPDATE_SQL, params);
        saveProperties(message.getMailId(), message.getProperties());
        return message;
    }

    private void saveProperties(long mailId, Map<String, String> properties) {
        jdbcTemplate.update(DELETE_PROPERTIES_SQL, Map.of("mailId", mailId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("mailId", mailId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_PROPERTY_SQL, batch);
    }

    private Map<String, String> loadProperties(long mailId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_PROPERTIES_SQL, Map.of("mailId", mailId));
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
