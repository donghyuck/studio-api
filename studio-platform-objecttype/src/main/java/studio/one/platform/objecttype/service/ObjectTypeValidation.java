package studio.one.platform.objecttype.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import studio.one.platform.objecttype.model.ObjectTypeStatus;

final class ObjectTypeValidation {

    private ObjectTypeValidation() {
    }

    static String normalizeStatus(String status) {
        ObjectTypeStatus s = ObjectTypeStatus.from(status);
        return s != null ? s.toStorage() : null;
    }

    static void validateStatus(String status) {
        if (ObjectTypeStatus.from(status) == null) {
            throw studio.one.platform.exception.PlatformRuntimeException.of(
                    studio.one.platform.objecttype.error.ObjectTypeErrorCodes.VALIDATION_ERROR,
                    "status");
        }
    }

    static String normalizeExt(String ext) {
        if (!StringUtils.hasText(ext)) {
            return null;
        }
        return Arrays.stream(ext.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    static String normalizeMime(String mime) {
        if (!StringUtils.hasText(mime)) {
            return null;
        }
        return Arrays.stream(mime.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }
}
