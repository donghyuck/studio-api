package studio.one.platform.textract.autoconfigure;

import java.time.Duration;
import java.util.List;
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

    private Pdf pdf = new Pdf();

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
        return java.lang.Math.toIntExact(bytes);
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

    public enum PdfEngine {
        AUTO,
        PDFBOX,
        PYMUPDF4LLM
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pdf {
        private PdfEngine engine = PdfEngine.AUTO;
        private boolean fallbackEnabled = true;
        private PdfEngines engines = new PdfEngines();
        private PdfAuto auto = new PdfAuto();
        private PdfOcrFallback ocrFallback = new PdfOcrFallback();
        private LargePdf largePdf = new LargePdf();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PdfEngines {
        private PdfBox pdfbox = new PdfBox();
        private PyMuPdf4Llm pymupdf4llm = new PyMuPdf4Llm();
        private Math math = new Math();
        private KoreanOcr koreanOcr = new KoreanOcr();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class KoreanOcr {
        private boolean enabled = false;
        @NotBlank
        private String endpoint = "http://localhost:8603/extract/pdf";
        private Duration timeout = Duration.ofMinutes(2);
        @NotBlank
        private String maxFileSize = "50MB";
        private int maxPages = 8;
        private int batchSize = 2;
        private boolean fallbackEnabled = false;
        @NotBlank
        private String fallbackEndpoint = "http://localhost:8000/extract/pdf";
        private int fallbackBatchSize = 2;

        public int getMaxFileSizeBytes() {
            return parseToBytes(maxFileSize, PREFIX + ".pdf.engines.korean-ocr.max-file-size");
        }
    }

    public enum MathProvider {
        NONE,
        PIX2TEXT,
        MATHPIX
    }

    public enum VisionProvider {
        NONE,
        GEMINI
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PdfBox {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PyMuPdf4Llm {
        private boolean enabled = false;
        @NotBlank
        private String endpoint = "http://localhost:8000/extract/pdf";
        private Duration timeout = Duration.ofSeconds(60);
        @NotBlank
        private String maxFileSize = "50MB";

        public int getMaxFileSizeBytes() {
            return parseToBytes(maxFileSize, PREFIX + ".pdf.engines.pymupdf4llm.max-file-size");
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Math {
        private boolean enabled = false;
        private MathProvider provider = MathProvider.NONE;
        private List<MathProvider> fallbackProviders = List.of(MathProvider.MATHPIX);
        private QualityGate qualityGate = new QualityGate();
        private Hybrid hybrid = new Hybrid();
        private VisionCorrection visionCorrection = new VisionCorrection();
        private Pix2Text pix2text = new Pix2Text();
        private Mathpix mathpix = new Mathpix();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QualityGate {
        private boolean enabled = true;
        private double minScore = 0.65d;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Hybrid {
        private boolean enabled = true;
        private int samplePages = 8;
        private int maxCorrectionPages = 8;
        private int fallbackPages = 2;
        private int waveSize = 2;
        private Duration timeBudget = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VisionCorrection {
        private boolean enabled = false;
        private VisionProvider provider = VisionProvider.GEMINI;
        private Gemini gemini = new Gemini();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Gemini {
        @NotBlank
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String apiKey = "";
        private String model = "gemini-2.5-flash";
        private Duration timeout = Duration.ofMinutes(2);
        @NotBlank
        private String maxFileSize = "20MB";

        public int getMaxFileSizeBytes() {
            return parseToBytes(maxFileSize, PREFIX + ".pdf.engines.math.vision-correction.gemini.max-file-size");
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pix2Text {
        @NotBlank
        private String endpoint = "http://localhost:8503/extract/pdf";
        private Duration timeout = Duration.ofMinutes(5);
        @NotBlank
        private String maxFileSize = "50MB";
        private String language = "ko,en";
        private boolean pageByPage = true;
        private int batchSize = 1;

        public int getMaxFileSizeBytes() {
            return parseToBytes(maxFileSize, PREFIX + ".pdf.engines.math.pix2text.max-file-size");
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Mathpix {
        @NotBlank
        private String apiBaseUrl = "https://api.mathpix.com/v3";
        private Duration timeout = Duration.ofMinutes(5);
        private Duration pollInterval = Duration.ofSeconds(2);
        private int maxPollAttempts = 90;
        private String appId = "";
        private String appKey = "";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PdfAuto {
        private PreferPyMuPdf4LlmWhen preferPymupdf4llmWhen = new PreferPyMuPdf4LlmWhen();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PdfOcrFallback {
        private boolean enabled = false;
        private int maxPages = 20;
        private float dpi = 180f;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LargePdf {
        private boolean enabled = true;
        private int pageThreshold = 100;
        private int batchSize = 50;
        private boolean continueOnPartFailure = true;
        private int maxPartFailures = 0;
        private boolean includeImages = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PreferPyMuPdf4LlmWhen {
        private int minPages = 3;
        private boolean tableDetectionRequired = true;
        private boolean ocrRequired = true;
        private boolean preserveLayout = true;
    }
}
