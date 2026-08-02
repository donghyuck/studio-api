package studio.one.application.webknowledge.application;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedDocument;

/**
 * Deterministically removes direct contact, government, and payment identifiers
 * before externally collected content is persisted or indexed.
 */
public final class WebKnowledgeContentSanitizer {

    static final String REDACTED_EMAIL = "[REDACTED_EMAIL]";
    static final String REDACTED_PHONE = "[REDACTED_PHONE]";
    static final String REDACTED_GOVERNMENT_ID = "[REDACTED_GOVERNMENT_ID]";
    static final String REDACTED_PAYMENT_ID = "[REDACTED_PAYMENT_ID]";

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.!#$%&'*+/=?^`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?\\.[a-z]{2,63}");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+\\d{1,3}[- .])|0)\\d{1,3}[- .]\\d{3,4}[- .]\\d{4}(?!\\d)");
    private static final Pattern KOREAN_PHONE_COMPACT = Pattern.compile(
            "(?<!\\d)(?:01[016789]\\d{7,8}|02\\d{7,8}|0[3-6][1-5]\\d{7,8})(?!\\d)");
    private static final Pattern KOREAN_RESIDENT_ID = Pattern.compile(
            "(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)");
    private static final Pattern PAYMENT_CARD_CANDIDATE = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    private final boolean enabled;

    public WebKnowledgeContentSanitizer(boolean enabled) {
        this.enabled = enabled;
    }

    public NormalizedDocument sanitize(NormalizedDocument document) {
        if (!enabled || document == null) {
            return document;
        }
        var blocks = document.blocks().stream()
                .map(this::sanitize)
                .toList();
        Map<String, Object> metadata = sanitizeMetadata(document.metadata());
        metadata = new LinkedHashMap<>(metadata);
        metadata.put("piiRedactionApplied", true);
        return new NormalizedDocument(
                document.sourceDocumentId(),
                sanitizeText(document.plainText()),
                document.sourceFormat(),
                document.filename(),
                blocks,
                Map.copyOf(metadata));
    }

    public String sanitizeText(String value) {
        if (!enabled || value == null || value.isBlank()) {
            return value;
        }
        String sanitized = replaceGovernmentIdentifiers(value);
        sanitized = replacePaymentIdentifiers(sanitized);
        sanitized = EMAIL.matcher(sanitized).replaceAll(REDACTED_EMAIL);
        sanitized = PHONE.matcher(sanitized).replaceAll(REDACTED_PHONE);
        return KOREAN_PHONE_COMPACT.matcher(sanitized).replaceAll(REDACTED_PHONE);
    }

    public Map<String, Object> sanitizeMetadata(Map<String, Object> values) {
        if (!enabled || values == null || values.isEmpty()) {
            return values == null ? Map.of() : Map.copyOf(values);
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            sanitized.put(key, value instanceof String text && !urlLike(key)
                    ? sanitizeText(text)
                    : value);
        });
        return Map.copyOf(sanitized);
    }

    private NormalizedBlock sanitize(NormalizedBlock block) {
        return new NormalizedBlock(
                block.id(),
                block.type(),
                sanitizeText(block.text()),
                block.sourceRef(),
                block.page(),
                block.slide(),
                block.order(),
                block.parentBlockId(),
                block.headingPath(),
                block.blockIds(),
                block.confidence(),
                sanitizeMetadata(block.metadata()));
    }

    private static String replacePaymentIdentifiers(String value) {
        Matcher matcher = PAYMENT_CARD_CANDIDATE.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            recognizedPaymentPrefix(digits) && luhnValid(digits)
                                    ? REDACTED_PAYMENT_ID
                                    : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceGovernmentIdentifiers(String value) {
        Matcher matcher = KOREAN_RESIDENT_ID.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            validBirthDatePrefix(digits)
                                    ? REDACTED_GOVERNMENT_ID
                                    : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean validBirthDatePrefix(String digits) {
        try {
            int month = Integer.parseInt(digits.substring(2, 4));
            int day = Integer.parseInt(digits.substring(4, 6));
            LocalDate.of(2000, month, day);
            return true;
        } catch (DateTimeException | NumberFormatException | IndexOutOfBoundsException ex) {
            return false;
        }
    }

    private static boolean recognizedPaymentPrefix(String digits) {
        if (digits.isEmpty()) {
            return false;
        }
        if (digits.startsWith("4")) {
            return digits.length() == 13 || digits.length() == 16 || digits.length() == 19;
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return digits.length() == 15;
        }
        if (digits.startsWith("6011") || digits.startsWith("65")) {
            return digits.length() == 16 || digits.length() == 19;
        }
        if (digits.length() != 16) {
            return false;
        }
        int prefix = Integer.parseInt(digits.substring(0, 4));
        int twoDigitPrefix = prefix / 100;
        return (twoDigitPrefix >= 51 && twoDigitPrefix <= 55)
                || (prefix >= 2221 && prefix <= 2720);
    }

    private static boolean luhnValid(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int value = digits.charAt(index) - '0';
            if (doubleDigit) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static boolean urlLike(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("url")
                || normalized.endsWith("uri")
                || normalized.endsWith("ref")
                || normalized.contains("hash");
    }
}
