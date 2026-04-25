package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.textract.service.FileContentExtractionService;

public interface AttachmentStructuredRagIndexer {

    boolean index(Attachment attachment,
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            FileContentExtractionService extractor,
            InputStream inputStream) throws IOException;
}
