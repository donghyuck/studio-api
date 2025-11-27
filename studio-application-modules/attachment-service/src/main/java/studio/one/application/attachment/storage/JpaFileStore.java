package studio.one.application.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

import javax.sql.rowset.serial.SerialBlob;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.entity.ApplicationAttachmentData;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.persistence.jpa.AttachmentDataJpaRepository;

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
                .map(this::asStream)
                .orElseThrow(() -> new RuntimeException("Attachment data not found"));
    }

    @Override
    public void delete(Attachment attachment) {
        attachmentDataRepository.deleteById(attachment.getAttachmentId());
    }

    private InputStream asStream(Blob blob) {
        try {
            return blob.getBinaryStream();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read attachment data", e);
        }
    }
}
