package studio.one.platform.thumbnail.autoconfigure;

import java.util.Locale;

import org.slf4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;

@ConfigurationProperties(prefix = ThumbnailProperties.PREFIX)
@Validated
@Getter
@Setter
public class ThumbnailProperties {

    public static final String FEATURE_PREFIX = "studio.features.thumbnail";
    public static final String PREFIX = "studio.thumbnail";
    public static final String LEGACY_ATTACHMENT_PREFIX = "studio.attachment.thumbnail";
    public static final String LEGACY_FEATURE_ATTACHMENT_PREFIX = "studio.features.attachment.thumbnail";
    public static final String MIGRATION_REASON =
            "Thumbnail generation defaults moved to the platform thumbnail service.";

    @Positive
    private int defaultSize = 128;

    @NotBlank
    private String defaultFormat = "png";

    @Positive
    private int minSize = 16;

    @Positive
    private int maxSize = 512;

    @NotBlank
    private String maxSourceSize = "50MB";

    @Positive
    private long maxSourcePixels = 25_000_000L;

    @Valid
    private Renderers renderers = new Renderers();

    public ThumbnailGenerationOptions generationOptions(Environment environment, Logger log) {
        int resolvedDefaultSize = resolveDefaultSize(environment, log);
        String resolvedDefaultFormat = resolveDefaultFormat(environment, log);
        long resolvedMaxSourceBytes = parseToBytes(maxSourceSize, PREFIX + ".max-source-size");
        return new ThumbnailGenerationOptions(
                resolvedDefaultSize,
                resolvedDefaultFormat,
                minSize,
                maxSize,
                resolvedMaxSourceBytes,
                maxSourcePixels);
    }

    private int resolveDefaultSize(Environment environment, Logger log) {
        return resolveLegacyLeafIfTargetMissing(environment, "default-size", Integer.class, defaultSize, log);
    }

    private String resolveDefaultFormat(Environment environment, Logger log) {
        return resolveLegacyLeafIfTargetMissing(environment, "default-format", String.class, defaultFormat, log);
    }

    private <T> T resolveLegacyLeafIfTargetMissing(
            Environment environment,
            String propertyName,
            Class<T> type,
            T targetValue,
            Logger log) {
        String targetKey = PREFIX + "." + propertyName;
        if (environment.containsProperty(targetKey)) {
            return targetValue;
        }
        String legacyAttachmentKey = LEGACY_ATTACHMENT_PREFIX + "." + propertyName;
        T legacyAttachmentValue = Binder.get(environment)
                .bind(legacyAttachmentKey, Bindable.of(type))
                .orElse(null);
        if (legacyAttachmentValue != null) {
            ConfigurationPropertyMigration.warnDeprecated(log, legacyAttachmentKey, targetKey, MIGRATION_REASON);
            return legacyAttachmentValue;
        }
        String legacyFeatureKey = LEGACY_FEATURE_ATTACHMENT_PREFIX + "." + propertyName;
        T legacyFeatureValue = Binder.get(environment)
                .bind(legacyFeatureKey, Bindable.of(type))
                .orElse(null);
        if (legacyFeatureValue != null) {
            ConfigurationPropertyMigration.warnDeprecated(log, legacyFeatureKey, targetKey, MIGRATION_REASON);
            return legacyFeatureValue;
        }
        return targetValue;
    }

    public static long parseToBytes(String value, String propertyName) {
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
        return bytes;
    }

    private static String normalizeDataSize(String value) {
        String trimmed = value.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.endsWith("KB") || upper.endsWith("MB") || upper.endsWith("GB") || upper.endsWith("TB")) {
            return upper;
        }
        if (upper.endsWith("K") || upper.endsWith("M") || upper.endsWith("G") || upper.endsWith("T")) {
            return upper + "B";
        }
        return trimmed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Renderers {
        @Valid
        private Renderer image = new Renderer();

        @Valid
        private PdfRenderer pdf = new PdfRenderer();

        @Valid
        private PptxRenderer pptx = new PptxRenderer();

        @Valid
        private Renderer docx = disabledRenderer();

        @Valid
        private Renderer hwp = disabledRenderer();

        @Valid
        private Renderer hwpx = disabledRenderer();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Renderer {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class PdfRenderer extends Renderer {
        private int page = 0;

        public PdfRenderer() {
            setEnabled(false);
        }
    }

    @Getter
    @Setter
    public static class PptxRenderer extends Renderer {
        private int slide = 0;

        public PptxRenderer() {
            setEnabled(false);
        }
    }

    private static Renderer disabledRenderer() {
        Renderer renderer = new Renderer();
        renderer.setEnabled(false);
        return renderer;
    }
}
