package studio.one.platform.documentconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;

class DocumentConvertJobTest {
    @Test
    void transitionsAndRetriesFailedJob() {
        Instant now = Instant.parse("2026-06-11T00:00:00Z");
        DocumentConvertJob job = DocumentConvertJob.pending("job-1", "1", DocumentFormat.MARKDOWN,
                DocumentFormat.PDF, "{}", "user", now);

        job.markRunning(now.plusSeconds(1));
        job.markFailed("PANDOC_TIMEOUT", "timeout", now.plusSeconds(2));
        job.prepareRetry(2, now.plusSeconds(3));

        assertEquals(DocumentConvertStatus.PENDING, job.status());
        assertEquals(1, job.retryCount());
    }

    @Test
    void rejectsUnsupportedConversionAndRetryAfterLimit() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> DocumentConvertJob.pending("job", "1",
                DocumentFormat.PDF, DocumentFormat.TEXT, "{}", null, now));
        DocumentConvertJob job = DocumentConvertJob.pending("job", "1", DocumentFormat.HTML,
                DocumentFormat.PDF, "{}", null, now);
        job.markRunning(now);
        job.markFailed("X", "failed", now);
        assertThrows(IllegalStateException.class, () -> job.prepareRetry(0, now));
    }
}
