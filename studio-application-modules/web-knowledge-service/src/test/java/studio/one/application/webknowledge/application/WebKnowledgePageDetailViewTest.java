package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgePageRevisionEntity;

class WebKnowledgePageDetailViewTest {

    @Test
    void exposesBoundedCurrentRevisionDetailWithoutFullSnapshot() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        WebKnowledgePageEntity page = new WebKnowledgePageEntity(
                "page-1", 2L, "source-1", "https://example.org/docs?a=1", "url-hash", now);
        page.seen("https://example.org/docs", "canonical-hash", now);
        page.currentRevision("revision-1", now);
        WebKnowledgePageRevisionEntity revision = new WebKnowledgePageRevisionEntity(
                "revision-1", 2L, "source-1", "page-1", "run-1", now);
        revision.fetched("text/html", 1234L, "etag", "last-modified", now, now);
        revision.normalized(
                "문서 제목", "Publisher", "ko", now, now,
                "content-hash", "full normalized snapshot", "bounded preview", "{\"kind\":\"article\"}", now);
        revision.complete(now);

        WebKnowledgePageDetailView detail = WebKnowledgePageDetailView.from(page, revision);

        assertThat(detail.pageId()).isEqualTo("page-1");
        assertThat(detail.url()).isEqualTo("https://example.org/docs");
        assertThat(detail.pageRevisionId()).isEqualTo("revision-1");
        assertThat(detail.revisionStatus()).isEqualTo("COMPLETED");
        assertThat(detail.contentPreview()).isEqualTo("bounded preview");
        assertThat(detail.contentLength()).isEqualTo(1234L);
        assertThat(detail.metadataJson()).contains("article");
        assertThat(detail.metadataTruncated()).isFalse();
        assertThat(detail.toString()).doesNotContain("full normalized snapshot");
    }

    @Test
    void truncatesUnusuallyLargeMetadataPayloads() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        WebKnowledgePageEntity page = new WebKnowledgePageEntity(
                "page-large", 2L, "source-1", "https://example.org/large", "url-hash", now);
        page.currentRevision("revision-large", now);
        WebKnowledgePageRevisionEntity revision = new WebKnowledgePageRevisionEntity(
                "revision-large", 2L, "source-1", "page-large", "run-1", now);
        revision.normalized(
                "문서 제목", null, "ko", null, null,
                "content-hash", "full snapshot", "preview",
                "x".repeat(16_484), now);

        WebKnowledgePageDetailView detail = WebKnowledgePageDetailView.from(page, revision);

        assertThat(detail.metadataJson()).hasSize(16_384);
        assertThat(detail.metadataTruncated()).isTrue();
    }
}
