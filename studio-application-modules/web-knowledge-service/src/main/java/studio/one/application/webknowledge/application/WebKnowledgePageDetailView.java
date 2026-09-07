package studio.one.application.webknowledge.application;

import java.net.URI;
import java.time.Instant;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionEntity;

/** Bounded page detail. The full normalized snapshot is intentionally not exposed. */
public record WebKnowledgePageDetailView(
        // Keep page detail payloads predictable even when crawled metadata is unusually large.
        // The complete normalized document is deliberately available only to the indexing pipeline.
        String pageId,
        Long workspaceId,
        String sourceId,
        String url,
        String canonicalUrl,
        String host,
        String path,
        boolean active,
        int missingRunCount,
        String pageRevisionId,
        String runId,
        String revisionStatus,
        String title,
        String publisher,
        String language,
        Instant publishedAt,
        Instant sourceModifiedAt,
        Instant retrievedAt,
        String contentType,
        Long contentLength,
        String contentHash,
        String contentPreview,
        String metadataJson,
        boolean metadataTruncated,
        String errorCode,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant updatedAt) {

    private static final int MAX_METADATA_PREVIEW_CHARS = 16_384;

    public static WebKnowledgePageDetailView from(
            WebKnowledgePageEntity page,
            WebKnowledgePageRevisionEntity revision) {
        URI normalized = safeUri(page.normalizedUrl());
        return new WebKnowledgePageDetailView(
                page.pageId(),
                page.workspaceId(),
                page.sourceId(),
                withoutQuery(page.normalizedUrl()),
                withoutQuery(page.canonicalUrl()),
                normalized == null ? null : normalized.getHost(),
                normalized == null || normalized.getPath() == null || normalized.getPath().isBlank()
                        ? "/"
                        : normalized.getPath(),
                page.active(),
                page.missingRunCount(),
                revision == null ? null : revision.pageRevisionId(),
                revision == null ? null : revision.runId(),
                revision == null ? null : revision.status(),
                revision == null ? null : revision.title(),
                revision == null ? null : revision.publisher(),
                revision == null ? null : revision.language(),
                revision == null ? null : revision.publishedAt(),
                revision == null ? null : revision.modifiedAt(),
                revision == null ? null : revision.retrievedAt(),
                revision == null ? null : revision.contentType(),
                revision == null ? null : revision.contentLength(),
                revision == null ? null : revision.contentHash(),
                revision == null ? null : revision.contentPreview(),
                revision == null ? null : boundedMetadata(revision.metadataJson()),
                revision != null && isMetadataTruncated(revision.metadataJson()),
                revision == null ? null : revision.errorCode(),
                page.firstSeenAt(),
                page.lastSeenAt(),
                page.updatedAt());
    }

    private static String boundedMetadata(String value) {
        if (!isMetadataTruncated(value)) {
            return value;
        }
        int endIndex = MAX_METADATA_PREVIEW_CHARS;
        if (Character.isHighSurrogate(value.charAt(endIndex - 1))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }

    private static boolean isMetadataTruncated(String value) {
        return value != null && value.length() > MAX_METADATA_PREVIEW_CHARS;
    }

    private static URI safeUri(String value) {
        try {
            return value == null ? null : URI.create(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String withoutQuery(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
