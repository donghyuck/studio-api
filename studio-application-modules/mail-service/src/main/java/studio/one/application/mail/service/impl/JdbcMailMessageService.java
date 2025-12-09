package studio.one.application.mail.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.model.DefaultMailMessage;
import studio.one.application.mail.domain.model.MailMessage;
import studio.one.application.mail.service.MailMessageService;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.exception.NotFoundException;

@Transactional
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

    @SqlStatement("data.mail.deleteProperties")
    private String deletePropertiesSql;

    @SqlStatement("data.mail.insertProperty")
    private String insertPropertySql;

    @SqlStatement("data.mail.findProperties")
    private String findPropertiesSql;

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

    public JdbcMailMessageService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
