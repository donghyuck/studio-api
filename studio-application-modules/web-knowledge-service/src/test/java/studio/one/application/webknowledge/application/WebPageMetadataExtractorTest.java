package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WebPageMetadataExtractorTest {

    @Test
    void extractsCanonicalTitlePublisherLanguageAndDates() {
        String html = """
                <html lang="ko">
                <head>
                  <title>fallback</title>
                  <meta property="og:title" content="공개 자료">
                  <meta property="og:site_name" content="Example Research">
                  <meta property="article:published_time" content="2026-07-01T12:00:00Z">
                  <link rel="canonical" href="/canonical">
                </head>
                <body><main><h1>제목</h1><p>본문입니다.</p></main></body>
                </html>
                """;

        var result = new WebPageMetadataExtractor().extract(
                html.getBytes(StandardCharsets.UTF_8),
                URI.create("https://example.org/article"));

        assertEquals("공개 자료", result.title());
        assertEquals("Example Research", result.publisher());
        assertEquals("ko", result.language());
        assertEquals("https://example.org/canonical", result.canonicalUri().toString());
        assertEquals("2026-07-01T12:00:00Z", result.publishedAt().toString());
    }
}
