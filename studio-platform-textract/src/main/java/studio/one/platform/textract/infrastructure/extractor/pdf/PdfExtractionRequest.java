package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Objects;

public record PdfExtractionRequest(
        byte[] bytes,
        String contentType,
        String filename,
        PdfExtractionOptions options) {

    public PdfExtractionRequest {
        bytes = Objects.requireNonNull(bytes, "bytes");
        options = options == null ? PdfExtractionOptions.defaults() : options;
    }
}
