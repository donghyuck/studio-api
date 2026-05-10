package studio.one.application.attachment.infrastructure.storage;

import java.io.InputStream;

import studio.one.application.attachment.domain.model.Attachment;

public interface FileStorageSaveResultCapable {

    FileStorageSaveResult saveWithResult(Attachment attachment, InputStream input);
}
