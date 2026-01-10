package studio.one.base.user.web.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformRuntimeException;

public final class RequestParamUtils {

    private RequestParamUtils() {
    }

    public static Optional<String> normalizeQuery(Optional<String> q) {
        if (q == null) {
            return Optional.empty();
        }
        return q.map(String::trim).filter(value -> !value.isBlank());
    }

    public static Set<String> parseFields(String raw, Set<String> allowedLower, Set<String> defaultFieldsLower) {
        if (raw == null || raw.isBlank()) {
            return defaultFieldsLower;
        }
        List<String> tokens = List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (tokens.isEmpty()) {
            return defaultFieldsLower;
        }
        Set<String> requested = tokens.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        Set<String> invalid = requested.stream()
                .filter(value -> !allowedLower.contains(value))
                .collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            String invalidList = invalid.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(","));
            throw PlatformRuntimeException.of(
                    ErrorType.of("error.validation.fields", HttpStatus.BAD_REQUEST),
                    invalidList);
        }
        return requested;
    }

    public static String allowedFieldsHeader(List<String> allowed) {
        List<String> sorted = new ArrayList<>(allowed);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", sorted);
    }
}
