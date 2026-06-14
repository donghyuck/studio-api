package studio.one.platform.documentconvert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobRepository;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertStoragePort;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertWorkerClient;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;

class DocumentConvertServiceTest {
    @Test
    void completesIdempotentlyAndRejectsUnexpectedResultKey() {
        InMemoryRepository repository = new InMemoryRepository();
        DocumentConvertStoragePort storage = new DocumentConvertStoragePort() {
            @Override
            public TransferUrls prepareTransfer(DocumentConvertJob job) {
                return new TransferUrls(URI.create("https://storage/source"),
                        URI.create("https://storage/upload"), "upload-token", resultFileId(job));
            }

            @Override
            public String resultFileId(DocumentConvertJob job) {
                return "document-conversions/" + job.jobId() + "/result.pdf";
            }

            @Override
            public URI resultDownloadUrl(DocumentConvertJob job) {
                return URI.create("https://storage/download");
            }
        };
        DocumentConvertWorkerClient worker = (job, source, upload, uploadToken, callback, resultFileId, options) -> { };
        DocumentConvertService service = new DocumentConvertService(repository, storage, worker,
                new ObjectMapper(), URI.create("https://api.example"), 2,
                Clock.fixed(Instant.parse("2026-06-11T00:00:00Z"), ZoneOffset.UTC));

        var created = service.create("1", "markdown", "pdf", Map.of("toc", true), "user");
        assertEquals(DocumentConvertStatus.RUNNING, created.status());
        assertThrows(IllegalArgumentException.class, () -> service.callback(created.jobId(), "COMPLETED",
                "other/key.pdf", null, null));

        String resultFileId = storage.resultFileId(repository.findByJobId(created.jobId()).orElseThrow());
        var completed = service.callback(created.jobId(), "COMPLETED", resultFileId, null, null);
        var duplicate = service.callback(created.jobId(), "COMPLETED", resultFileId, null, null);

        assertEquals(DocumentConvertStatus.COMPLETED, completed.status());
        assertEquals(completed, duplicate);
    }

    private static final class InMemoryRepository implements DocumentConvertJobRepository {
        private final Map<String, DocumentConvertJob> jobs = new LinkedHashMap<>();

        @Override
        public DocumentConvertJob save(DocumentConvertJob job) {
            jobs.put(job.jobId(), job);
            return job;
        }

        @Override
        public Optional<DocumentConvertJob> findByJobId(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }
    }
}
