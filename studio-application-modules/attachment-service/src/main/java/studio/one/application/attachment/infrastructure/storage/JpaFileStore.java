package studio.one.application.attachment.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.rowset.serial.SerialBlob;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import studio.one.application.attachment.domain.model.ApplicationAttachmentData;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.infrastructure.persistence.jpa.AttachmentDataJpaRepository;

public class JpaFileStore implements FileStorage {

    private final AttachmentDataJpaRepository attachmentDataRepository;
    private final TransactionTemplate transactionTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JpaFileStore(AttachmentDataJpaRepository attachmentDataRepository) {
        this(attachmentDataRepository, null, null);
    }

    public JpaFileStore(
            AttachmentDataJpaRepository attachmentDataRepository,
            PlatformTransactionManager transactionManager) {
        this(attachmentDataRepository, transactionManager, null);
    }

    public JpaFileStore(
            AttachmentDataJpaRepository attachmentDataRepository,
            PlatformTransactionManager transactionManager,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.attachmentDataRepository = attachmentDataRepository;
        this.transactionTemplate = transactionManager == null ? null : readOnlyTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String save(Attachment attachment, InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            Blob blob = new SerialBlob(bytes);
            ApplicationAttachmentData data = new ApplicationAttachmentData();
            data.setAttachmentId(attachment.getAttachmentId());
            data.setBlob(blob);
            attachmentDataRepository.save(data);
            return String.valueOf(attachment.getAttachmentId());
        } catch (IOException | SQLException e) {
            throw new RuntimeException("JPA file save failed", e);
        }
    }

    @Override
    public InputStream load(Attachment attachment) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> loadInternal(attachment));
        }
        return loadInternal(attachment);
    }

    private InputStream loadInternal(Attachment attachment) {
        if (jdbcTemplate != null) {
            try {
                return loadWithJdbc(attachment);
            } catch (RuntimeException ignored) {
                // PostgreSQL large object reads need the native path; other DBs can keep using JPA Blob reads.
            }
        }
        return loadWithJpa(attachment);
    }

    private InputStream loadWithJpa(Attachment attachment) {
        return attachmentDataRepository.findById(attachment.getAttachmentId())
                .map(ApplicationAttachmentData::getBlob)
                .map(this::asByteArrayStream)
                .orElseThrow(() -> new RuntimeException("Attachment data not found"));
    }

    @Override
    public void delete(Attachment attachment) {
        attachmentDataRepository.deleteById(attachment.getAttachmentId());
    }

    private InputStream asByteArrayStream(Blob blob) {
        try {
            long length = blob.length();
            if (length > Integer.MAX_VALUE) {
                throw new RuntimeException("Attachment data is too large to read");
            }
            return new ByteArrayInputStream(blob.getBytes(1, (int) length));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read attachment data", e);
        }
    }

    private InputStream loadWithJdbc(Attachment attachment) {
        byte[] bytes = jdbcTemplate.query("""
                SELECT lo_get(ATTACHMENT_DATA)
                  FROM TB_APPLICATION_ATTACHMENT_DATA
                 WHERE ATTACHMENT_ID = :attachmentId
                """, Map.of("attachmentId", attachment.getAttachmentId()), rs -> {
            if (!rs.next()) {
                return null;
            }
            return rs.getBytes(1);
        });
        if (bytes == null) {
            throw new RuntimeException("Attachment data not found");
        }
        return new ByteArrayInputStream(bytes);
    }

    private TransactionTemplate readOnlyTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }
}
