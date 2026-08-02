package studio.one.application.webknowledge.application;

import java.time.Instant;
import java.net.URI;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageJpaRepository;

public record WebKnowledgePageView(
        String url,
        String canonicalUrl,
        String host,
        String path,
        String title,
        String status,
        boolean active,
        int missingRunCount,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant updatedAt) {

    public static WebKnowledgePageView from(WebKnowledgePageJpaRepository.PageSummary page) {
        URI normalized = safeUri(page.getNormalizedUrl());
        return new WebKnowledgePageView(
                withoutQuery(page.getNormalizedUrl()),
                withoutQuery(page.getCanonicalUrl()),
                normalized == null ? null : normalized.getHost(),
                normalized == null ? null : path(normalized),
                page.getTitle(),
                page.getActive() ? "ACTIVE" : "REMOVED",
                page.getActive(),
                page.getMissingRunCount(),
                page.getFirstSeenAt(),
                page.getLastSeenAt(),
                page.getUpdatedAt());
    }

    private static URI safeUri(String value) {
        try {
            return value == null ? null : URI.create(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String path(URI uri) {
        return uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
    }

    private static String withoutQuery(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null).toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
