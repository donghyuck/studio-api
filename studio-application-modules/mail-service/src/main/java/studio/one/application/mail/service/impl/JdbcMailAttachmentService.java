package studio.one.application.mail.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.model.DefaultMailAttachment;
import studio.one.application.mail.domain.model.MailAttachment;
import studio.one.application.mail.service.MailAttachmentService;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;

@Transactional
@Service(MailAttachmentService.SERVICE_NAME)
public class JdbcMailAttachmentService implements MailAttachmentService {

    @SqlStatement("data.mail.insertAttachment")
    private String insertAttachmentSql;

    @SqlStatement("data.mail.deleteAttachmentsByMail")
    private String deleteAttachmentsByMailSql;

    @SqlStatement("data.mail.findAttachmentsByMail")
    private String findAttachmentsByMailSql;

    private static final RowMapper<MailAttachment> ROW_MAPPER = (rs, rowNum) -> {
        DefaultMailAttachment attachment = new DefaultMailAttachment();
        attachment.setAttachmentId(rs.getLong("ATTACHMENT_ID"));
        attachment.setMailId(rs.getLong("MAIL_ID"));
        attachment.setFilename(rs.getString("FILENAME"));
        attachment.setContentType(rs.getString("CONTENT_TYPE"));
        attachment.setSize(rs.getLong("SIZE"));
        attachment.setContent(rs.getBytes("CONTENT"));
        Timestamp created = rs.getTimestamp("CREATED_AT");
        Timestamp updated = rs.getTimestamp("UPDATED_AT");
        attachment.setCreatedAt(created != null ? created.toInstant() : null);
        attachment.setUpdatedAt(updated != null ? updated.toInstant() : null);
        return attachment;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMailAttachmentService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replaceAttachments(long mailId, List<MailAttachment> attachments) {
        jdbcTemplate.update(deleteAttachmentsByMailSql, Map.of("mailId", mailId));
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<MapSqlParameterSource> batch = attachments.stream()
                .map(att -> new MapSqlParameterSource()
                        .addValue("mailId", mailId)
                        .addValue("filename", att.getFilename())
                        .addValue("contentType", att.getContentType())
                        .addValue("size", att.getSize())
                        .addValue("content", att.getContent())
                        .addValue("createdAt", toTimestamp(att.getCreatedAt(), now))
                        .addValue("updatedAt", Timestamp.from(now)))
                .toList();
        jdbcTemplate.batchUpdate(insertAttachmentSql, batch.toArray(new MapSqlParameterSource[0]));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailAttachment> findByMailId(long mailId) {
        return jdbcTemplate.query(findAttachmentsByMailSql, Map.of("mailId", mailId), ROW_MAPPER);
    }

    private Timestamp toTimestamp(Instant value, Instant defaultInstant) {
        return Timestamp.from(value == null ? defaultInstant : value);
    }
}
