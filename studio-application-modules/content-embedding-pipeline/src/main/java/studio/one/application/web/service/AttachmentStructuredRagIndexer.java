package studio.one.application.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.textract.service.FileContentExtractionService;

public interface AttachmentStructuredRagIndexer {

    /**
     * Attempts structure-aware indexing for an attachment.
     *
     * @return {@code true} when indexing was fully handled. {@code false} means the
     * caller must use its fallback path and must not reuse the supplied
     * {@link InputStream}.
     */
    boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream) throws IOException;
}
