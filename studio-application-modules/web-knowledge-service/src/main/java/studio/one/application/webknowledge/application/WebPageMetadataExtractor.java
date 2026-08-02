package studio.one.application.webknowledge.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class WebPageMetadataExtractor {

    public Metadata extract(byte[] body, URI finalUri) {
        Document document = Jsoup.parse(new String(body, StandardCharsets.UTF_8), finalUri.toString());
        String title = bounded(first(
                meta(document, "property", "og:title"),
                meta(document, "name", "twitter:title"),
                document.title(),
                text(document.selectFirst("h1"))), 500);
        String publisher = bounded(first(
                meta(document, "property", "og:site_name"),
                meta(document, "name", "author"),
                finalUri.getHost()), 300);
        String language = bounded(first(document.selectFirst("html") == null
                ? null : document.selectFirst("html").attr("lang"), null), 32);
        Instant publishedAt = instant(first(
                meta(document, "property", "article:published_time"),
                meta(document, "name", "date"),
                meta(document, "name", "DC.date")));
        Instant modifiedAt = instant(first(
                meta(document, "property", "article:modified_time"),
                meta(document, "name", "last-modified")));
        URI canonicalUri = canonical(document, finalUri);
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "title", title);
        put(metadata, "publisher", publisher);
        put(metadata, "language", language);
        put(metadata, "canonicalUrl", canonicalUri.toString());
        if (publishedAt != null) {
            metadata.put("publishedAt", publishedAt.toString());
        }
        if (modifiedAt != null) {
            metadata.put("modifiedAt", modifiedAt.toString());
        }
        return new Metadata(title, publisher, language, publishedAt, modifiedAt, canonicalUri, Map.copyOf(metadata));
    }

    private URI canonical(Document document, URI fallback) {
        Element element = document.selectFirst("link[rel=canonical][href]");
        if (element == null || element.attr("href").isBlank()) {
            return fallback;
        }
        try {
            URI candidate = fallback.resolve(element.attr("href"));
            return "https".equalsIgnoreCase(candidate.getScheme()) && candidate.getHost() != null
                    ? candidate
                    : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String meta(Document document, String attribute, String value) {
        Element element = document.selectFirst("meta[" + attribute + "=\"" + value + "\"][content]");
        return element == null ? null : text(element.attr("content"));
    }

    private static String text(Element element) {
        return element == null ? null : text(element.text());
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String bounded(String value, int maxLength) {
        String normalized = text(value);
        return normalized == null || normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String first(String... values) {
        for (String value : values) {
            String normalized = text(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ex) {
            try {
                return value == null ? null : Instant.parse(value);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    public record Metadata(
            String title,
            String publisher,
            String language,
            Instant publishedAt,
            Instant modifiedAt,
            URI canonicalUri,
            Map<String, Object> values) {
    }
}
