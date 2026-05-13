package studio.one.platform.textract.infrastructure.extractor.pdf;

import java.util.Objects;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class PdfExtractionRequest {
    byte[] bytes;
    String contentType;
    String filename;
    PdfExtractionOptions options;


    public PdfExtractionRequest(byte[] bytes, String contentType, String filename, PdfExtractionOptions options) {
        bytes = Objects.requireNonNull(bytes, "bytes");
        options = options == null ? PdfExtractionOptions.defaults() : options;


        this.bytes = bytes;


        this.contentType = contentType;


        this.filename = filename;


        this.options = options;


    }
}
