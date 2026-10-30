package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;

class RagIndexJobEndpointSecurityTest {

    @Test
    void detectsAttachmentSourceRequests() {
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(null));

        assertThat(security.isAttachmentSource(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null,
                Map.of(),
                List.of(),
                false))).isTrue();
    }

    @Test
    void detectsAttachmentCreateRequestsByObjectTypeWhenSourceTypeIsRaw() {
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(null));

        assertThat(security.isAttachmentSource(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                "content",
                Map.of(),
                List.of(),
                false))).isTrue();
    }

    @Test
    void detectsAttachmentJobsForRetryAuthorization() {
        RagIndexJob job = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "attachment",
                Instant.parse("2026-04-26T00:00:00Z"));
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(job));

        assertThat(security.isAttachmentJob("job-1")).isTrue();
    }

    @Test
    void detectsAttachmentJobsByObjectTypeWhenSourceTypeIsRaw() {
        RagIndexJob job = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "raw",
                Instant.parse("2026-04-26T00:00:00Z"));
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(job));

        assertThat(security.isAttachmentJob("job-1")).isTrue();
    }

    @Test
    void detectsAttachmentObjectScopesForReadAuthorization() {
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(null));

        assertThat(security.isAttachmentObject("attachment")).isTrue();
        assertThat(security.isAttachmentObject("article")).isFalse();
    }

    @Test
    void canWriteJobRequiresStoredObjectWriteAuthorization() {
        RagIndexJob job = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "attachment",
                Instant.parse("2026-04-26T00:00:00Z"));
        RagIndexJobEndpointSecurity security = new RagIndexJobEndpointSecurity(new StubJobService(job));
        RagObjectAuthorizationRouter router = org.mockito.Mockito.mock(RagObjectAuthorizationRouter.class);
        org.mockito.Mockito.when(router.canWrite("attachment", "42")).thenReturn(true);

        assertThat(security.canWriteJob("job-1", router)).isTrue();
    }

    @Test
    void canWriteJobFailsClosedForMissingJobOrScope() {
        RagIndexJobEndpointSecurity missing = new RagIndexJobEndpointSecurity(new StubJobService(null));
        RagObjectAuthorizationRouter router = org.mockito.Mockito.mock(RagObjectAuthorizationRouter.class);

        assertThat(missing.canWriteJob("job-1", router)).isFalse();

        RagIndexJob unscoped = RagIndexJob.pending(
                "job-1",
                null,
                null,
                "doc-1",
                "raw",
                Instant.parse("2026-04-26T00:00:00Z"));
        RagIndexJobEndpointSecurity noScope = new RagIndexJobEndpointSecurity(new StubJobService(unscoped));
        assertThat(noScope.canWriteJob("job-1", router)).isFalse();
    }

    private record StubJobService(RagIndexJob job) implements RagIndexJobService {

        @Override
        public RagIndexJob createJob(RagIndexJobCreateRequest request) {
            return job;
        }

        @Override
        public RagIndexJob startJob(String jobId) {
            return job;
        }

        @Override
        public RagIndexJob retryJob(String jobId) {
            return job;
        }

        @Override
        public Optional<RagIndexJob> getJob(String jobId) {
            return Optional.ofNullable(job);
        }

        @Override
        public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
            return new RagIndexJobPage(List.of(), 0, pageable.offset(), pageable.limit());
        }

        @Override
        public List<RagIndexJobLog> getLogs(String jobId) {
            return List.of();
        }

        @Override
        public RagIndexProgressListener progressListener(String jobId) {
            return RagIndexProgressListener.noop();
        }
    }
}
