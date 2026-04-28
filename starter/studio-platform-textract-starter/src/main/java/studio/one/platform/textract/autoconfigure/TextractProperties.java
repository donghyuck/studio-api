package studio.one.platform.textract.autoconfigure;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ConfigurationProperties(prefix = TextractProperties.PREFIX)
@Validated
@Getter
@Setter
public class TextractProperties {

    public static final String FEATURE_PREFIX = "studio.features.textract";
    public static final String LEGACY_FEATURE_PREFIX = "studio.features.text";
    public static final String PREFIX = "studio.textract";
    public static final String LEGACY_RUNTIME_PREFIX = "studio.text";
    public static final String MAX_EXTRACT_SIZE_PROPERTY = PREFIX + ".max-extract-size";
    public static final String LEGACY_RUNTIME_MAX_EXTRACT_SIZE_PROPERTY =
            LEGACY_RUNTIME_PREFIX + ".max-extract-size";
    public static final String LEGACY_FEATURE_MAX_EXTRACT_BYTES_PROPERTY =
            LEGACY_FEATURE_PREFIX + ".max-extract-bytes";

    @NotBlank
    private String maxExtractSize = "10MB";

    private Tesseract tesseract = new Tesseract();

    public int getMaxExtractBytes() {
        return parseToBytes(maxExtractSize, MAX_EXTRACT_SIZE_PROPERTY);
    }

    static int parseToBytes(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }

        String normalized = normalizeDataSize(value);
        DataSize dataSize;
        try {
            dataSize = DataSize.parse(normalized, DataUnit.BYTES);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(propertyName
                    + " must be a positive data size such as 10M, 10MB, or 10485760", ex);
        }

        long bytes = dataSize.toBytes();
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(propertyName + " must be between 1B and "
                    + Integer.MAX_VALUE + "B");
        }
        return Math.toIntExact(bytes);
    }

    private static String normalizeDataSize(String value) {
        String trimmed = value.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith("K") || upper.endsWith("M") || upper.endsWith("G") || upper.endsWith("T")) {
            return trimmed + "B";
        }
        return trimmed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Tesseract {
        private String datapath = "/usr/share/tesseract-ocr/4.00/tessdata";
        private String language = "kor+eng";
    }
}
