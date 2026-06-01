package studio.one.application.attachment.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.sql.SQLException;

import javax.sql.rowset.serial.SerialBlob;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.ApplicationAttachmentData;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.infrastructure.persistence.jpa.AttachmentDataJpaRepository;

@RequiredArgsConstructor
public class JpaFileStore implements FileStorage {

    private final AttachmentDataJpaRepository attachmentDataRepository;

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
}
