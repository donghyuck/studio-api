package studio.one.application.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;

@RequiredArgsConstructor
public class JdbcFileStore implements FileStorage {

    private static final String TABLE = "TB_APPLICATION_ATTACHMENT_DATA";

    private final NamedParameterJdbcTemplate template;
    private final LobHandler lobHandler = new DefaultLobHandler();

    @Override
    public String save(Attachment attachment, InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            SqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", attachment.getAttachmentId())
                    .addValue("payload", new SqlLobValue(bytes, lobHandler), Types.BLOB);
            int updated = template.update(
                    "update %s set ATTACHMENT_DATA = :payload where ATTACHMENT_ID = :id".formatted(TABLE), params);
            if (updated == 0) {
                template.update(
                        "insert into %s (ATTACHMENT_ID, ATTACHMENT_DATA) values (:id, :payload)".formatted(TABLE),
                        params);
            }
            return String.valueOf(attachment.getAttachmentId());
        } catch (IOException e) {
            throw new RuntimeException("JDBC file save failed", e);
        }
    }

    @Override
    public InputStream load(Attachment attachment) {
        String sql = "select ATTACHMENT_DATA from %s where ATTACHMENT_ID = :id".formatted(TABLE);
        List<InputStream> streams = template.query(sql, Map.of("id", attachment.getAttachmentId()),
                (rs, rowNum) -> lobHandler.getBlobAsBinaryStream(rs, "ATTACHMENT_DATA"));
        if (streams.isEmpty() || streams.get(0) == null) {
            throw new RuntimeException("Attachment data not found");
        }
        return streams.get(0);
    }

    @Override
    public void delete(Attachment attachment) {
        template.update("delete from %s where ATTACHMENT_ID = :id".formatted(TABLE),
                Map.of("id", attachment.getAttachmentId()));
    }
}
