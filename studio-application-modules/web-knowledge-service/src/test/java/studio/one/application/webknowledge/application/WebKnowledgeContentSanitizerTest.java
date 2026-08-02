package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class WebKnowledgeContentSanitizerTest {

    private final WebKnowledgeContentSanitizer sanitizer = new WebKnowledgeContentSanitizer(true);

    @Test
    void redactsDirectIdentifiersBeforePersistenceAndChunking() {
        String source = """
                담당자 test@example.org, 전화 +82-10-1234-5678, 보조 연락처 01012345678,
                주민번호 900101-1234567, 카드 4111 1111 1111 1111
                """;
        NormalizedBlock block = NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, source)
                .id("block-1")
                .build();
        NormalizedDocument document = new NormalizedDocument(
                "revision-1",
                source,
                "text/html",
                "page.html",
                List.of(block),
                Map.of("author", "test@example.org"));

        NormalizedDocument sanitized = sanitizer.sanitize(document);

        assertFalse(sanitized.chunkableText().contains("test@example.org"));
        assertFalse(sanitized.chunkableText().contains("01012345678"));
        assertFalse(sanitized.chunkableText().contains("1234567"));
        assertTrue(sanitized.chunkableText().contains(WebKnowledgeContentSanitizer.REDACTED_EMAIL));
        assertTrue(sanitized.chunkableText().contains(WebKnowledgeContentSanitizer.REDACTED_PHONE));
        assertTrue(sanitized.chunkableText().contains(WebKnowledgeContentSanitizer.REDACTED_GOVERNMENT_ID));
        assertTrue(sanitized.chunkableText().contains(WebKnowledgeContentSanitizer.REDACTED_PAYMENT_ID));
        assertEquals(
                WebKnowledgeContentSanitizer.REDACTED_EMAIL,
                sanitized.metadata().get("author"));
        assertEquals(true, sanitized.metadata().get("piiRedactionApplied"));
    }

    @Test
    void preservesUrlsAndNonPaymentIdentifiers() {
        Map<String, Object> metadata = sanitizer.sanitizeMetadata(Map.of(
                "canonicalUrl", "https://example.org/test@example.org",
                "isbn", "9780306406157"));

        assertEquals("https://example.org/test@example.org", metadata.get("canonicalUrl"));
        assertEquals("9780306406157", metadata.get("isbn"));
    }

    @Test
    void canBeDisabledForExplicitCompatibilityNeeds() {
        WebKnowledgeContentSanitizer disabled = new WebKnowledgeContentSanitizer(false);

        assertEquals("test@example.org", disabled.sanitizeText("test@example.org"));
    }
}
