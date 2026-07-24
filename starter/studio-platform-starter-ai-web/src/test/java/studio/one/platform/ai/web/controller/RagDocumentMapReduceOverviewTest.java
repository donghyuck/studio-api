package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RagDocumentMapReduceOverviewTest {

    private final RagDocumentMapReduceOverview overview = new RagDocumentMapReduceOverview();

    @Test
    void reducesLargeDocumentsInOrderAndReusesTheContentFingerprint() {
        AtomicInteger calls = new AtomicInteger();
        String context = "문서 제목: Sample Book\n원본 파일: book.epub\nfirst section\n"
                + "a".repeat(1_100) + "\nlast section\n" + "b".repeat(1_100);

        RagDocumentMapReduceOverview.Reduction first = overview.reduce(
                "attachment", "3", "gemini", "flash", context, 1_000, 1_000,
                prompt -> "summary-" + calls.incrementAndGet());
        RagDocumentMapReduceOverview.Reduction second = overview.reduce(
                "attachment", "3", "gemini", "flash", context, 1_000, 1_000,
                prompt -> "unused");

        assertThat(first.applied()).isTrue();
        assertThat(first.cacheHit()).isFalse();
        assertThat(first.segmentCount()).isGreaterThan(1);
        assertThat(first.context()).contains("summary-1", "summary-2");
        assertThat(first.context()).contains("문서 제목: Sample Book", "원본 파일: book.epub");
        assertThat(second.context()).isEqualTo(first.context());
        assertThat(second.cacheHit()).isTrue();
        assertThat(calls).hasValue(first.segmentCount());
    }

    @Test
    void doesNotReduceSmallDocuments() {
        RagDocumentMapReduceOverview.Reduction reduction = overview.reduce(
                "attachment", "3", "gemini", "flash", "short", 100, 1_000,
                prompt -> "not called");

        assertThat(reduction.applied()).isFalse();
        assertThat(reduction.context()).isEqualTo("short");
    }

    @Test
    void changedContentDoesNotReuseTheCachedSummary() {
        AtomicInteger calls = new AtomicInteger();
        overview.reduce("attachment", "3", "gemini", "flash", "a".repeat(1_200), 1_000, 1_000,
                prompt -> "summary-" + calls.incrementAndGet());
        overview.reduce("attachment", "3", "gemini", "flash", "b".repeat(1_200), 1_000, 1_000,
                prompt -> "summary-" + calls.incrementAndGet());

        assertThat(calls).hasValue(4);
    }

    @Test
    void removesIntermediateCitationNumbersFromSegmentSummaries() {
        RagDocumentMapReduceOverview.Reduction reduction = overview.reduce(
                "attachment",
                "3",
                "gemini",
                "flash",
                "a".repeat(1_200),
                1_000,
                1_000,
                prompt -> "첫 주장 [1]과 두 번째 주장 [2, 3]");

        assertThat(reduction.context())
                .contains("첫 주장 과 두 번째 주장")
                .doesNotContain("[1]", "[2, 3]");
    }
}
