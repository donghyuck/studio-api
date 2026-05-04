package studio.one.application.attachment.storage;

import java.net.URL;
import java.time.Duration;

import studio.one.application.attachment.domain.model.Attachment;

public interface SignedDownloadUrlFileStorage {

    URL createSignedDownloadUrl(Attachment attachment, Duration ttl, String contentDisposition);
}
