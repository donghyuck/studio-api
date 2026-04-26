package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.RagIndexChunkDto;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;
import studio.one.platform.ai.web.dto.RagIndexJobDto;
import studio.one.platform.ai.web.dto.RagIndexJobListResponseDto;
import studio.one.platform.ai.web.dto.RagIndexJobLogDto;
import studio.one.platform.web.dto.ApiResponse;

class RagIndexJobControllerTest {

    @Test
    void createJobBuildsIndexRequestAndReturnsAcceptedJob() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        ResponseEntity<ApiResponse<RagIndexJobDto>> response = controller.createJob(
                new RagIndexJobCreateRequestDto(
                        "attachment",
                        "42",
                        "doc-1",
                        "attachment",
                        false,
                        "hello",
                        Map.of("category", "manual"),
                        List.of("alpha"),
                        false));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().getData().jobId()).isEqualTo("job-1");
        assertThat(jobService.createdRequest.indexRequest().metadata())
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "42")
                .containsEntry("category", "manual");
    }

    @Test
    void createJobAcceptsSourceRequestWithoutText() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        ResponseEntity<ApiResponse<RagIndexJobDto>> response = controller.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null,
                Map.of("attachmentId", "42"),
                List.of("alpha"),
                false));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(jobService.createdRequest.indexRequest()).isNull();
        assertThat(jobService.createdRequest.sourceType()).isEqualTo("attachment");
        assertThat(jobService.createdRequest.documentId()).isEqualTo("doc-1");
        assertThat(jobService.createdSourceRequest.metadata()).containsEntry("attachmentId", "42");
    }

    @Test
    void createAttachmentSourceJobDefaultsDocumentIdBeforePersistingJob() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        controller.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                null,
                "attachment",
                false,
                null,
                Map.of(),
                List.of(),
                false));

        assertThat(jobService.createdRequest.documentId()).isEqualTo("42");
        assertThat(jobService.createdSourceRequest.metadata()).containsEntry("attachmentId", "42");
    }

    @Test
    void createJobRejectsMissingTextAndSourceType() {
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(),
                mock(RagPipelineService.class),
                null);

        assertThatThrownBy(() -> controller.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                null,
                false,
                null,
                Map.of(),
                List.of(),
                false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void listsFetchesRetriesAndReturnsLogs() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        ResponseEntity<ApiResponse<RagIndexJobListResponseDto>> listResponse =
                controller.listJobs(RagIndexJobStatus.PENDING, "attachment", "42", null, 0, 10, "createdAt", "desc");
        ResponseEntity<ApiResponse<RagIndexJobDto>> detailResponse = controller.getJob("job-1");
        ResponseEntity<ApiResponse<RagIndexJobDto>> retryResponse = controller.retryJob("job-1");
        ResponseEntity<ApiResponse<List<RagIndexJobLogDto>>> logsResponse = controller.getLogs("job-1");

        assertThat(listResponse.getBody().getData().items()).hasSize(1);
        assertThat(detailResponse.getBody().getData().jobId()).isEqualTo("job-1");
        assertThat(retryResponse.getStatusCode().value()).isEqualTo(202);
        assertThat(logsResponse.getBody().getData())
                .extracting(RagIndexJobLogDto::code)
                .containsExactly(RagIndexJobLogCode.JOB_STARTED);
        assertThat(jobService.sort.field()).isEqualTo(RagIndexJobSort.Field.CREATED_AT);
        assertThat(jobService.sort.direction()).isEqualTo(RagIndexJobSort.Direction.DESC);
    }

    @Test
    void listJobsMapsSortAliasesAndDirectionFallback() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        controller.listJobs(null, null, null, null, 0, 10, " document-id ", "sideways");

        assertThat(jobService.sort.field()).isEqualTo(RagIndexJobSort.Field.DOCUMENT_ID);
        assertThat(jobService.sort.direction()).isEqualTo(RagIndexJobSort.Direction.DESC);
    }

    @Test
    void listJobsBindsSortQueryParametersThroughMvc() throws Exception {
        CapturingJobService jobService = new CapturingJobService();
        MockMvc mockMvc = jobControllerMockMvc(jobService);

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs")
                        .param("sort", " document-id ")
                        .param("direction", "sideways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].jobId").value("job-1"));

        assertThat(jobService.sort.field()).isEqualTo(RagIndexJobSort.Field.DOCUMENT_ID);
        assertThat(jobService.sort.direction()).isEqualTo(RagIndexJobSort.Direction.DESC);
    }

    @Test
    void listJobsUsesDefaultSortThroughMvc() throws Exception {
        CapturingJobService jobService = new CapturingJobService();
        MockMvc mockMvc = jobControllerMockMvc(jobService);

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs"))
                .andExpect(status().isOk());

        assertThat(jobService.sort.field()).isEqualTo(RagIndexJobSort.Field.CREATED_AT);
        assertThat(jobService.sort.direction()).isEqualTo(RagIndexJobSort.Direction.DESC);
    }

    @Test
    void listJobsWithLegacyServiceFallsBackToTwoArgListJobsThroughMvc() throws Exception {
        LegacyListJobService jobService = new LegacyListJobService();
        MockMvc mockMvc = jobControllerMockMvc(jobService);

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs")
                        .param("sort", "documentId")
                        .param("direction", "asc"))
                .andExpect(status().isOk());

        assertThat(jobService.usedLegacyListJobs).isTrue();
    }

    @Test
    void retryRejectsActiveJob() {
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(RagIndexJobStatus.RUNNING),
                mock(RagPipelineService.class),
                null);

        assertThatThrownBy(() -> controller.retryJob("job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void objectChunksReuseRagPipelineListByObject() {
        RagIndexJobService jobService = new CapturingJobService();
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        RagIndexJobController controller = new RagIndexJobController(jobService, ragPipelineService, null);
        when(ragPipelineService.listByObject("attachment", "42", 25))
                .thenReturn(List.of(new RagSearchResult("doc-1", "chunk text", Map.of(
                        VectorRecord.KEY_CHUNK_ID, "chunk-1",
                        VectorRecord.KEY_DOCUMENT_ID, "doc-1",
                        VectorRecord.KEY_PARENT_CHUNK_ID, "parent-1",
                        VectorRecord.KEY_CHUNK_TYPE, "child",
                        VectorRecord.KEY_HEADING_PATH, List.of("Intro", "Details"),
                        VectorRecord.KEY_SOURCE_REF, "sample.txt#page=1",
                        VectorRecord.KEY_PAGE, 1,
                        "chunkOrder", 7,
                        "indexedAt", "2026-04-26T00:00:00Z"), 0.8d)));

        ResponseEntity<ApiResponse<List<RagIndexChunkDto>>> response =
                controller.objectChunks("attachment", "42", 25);

        RagIndexChunkDto chunk = response.getBody().getData().get(0);
        assertThat(chunk.chunkId()).isEqualTo("chunk-1");
        assertThat(chunk.parentChunkId()).isEqualTo("parent-1");
        assertThat(chunk.headingPath()).isEqualTo("Intro > Details");
        assertThat(chunk.indexedAt()).isEqualTo(java.time.Instant.parse("2026-04-26T00:00:00Z"));
        verify(ragPipelineService).listByObject("attachment", "42", 25);
    }

    @Test
    void objectMetadataUsesVectorStorePort() {
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(),
                mock(RagPipelineService.class),
                vectorStorePort);
        when(vectorStorePort.getMetadata("attachment", "42")).thenReturn(Map.of("documentId", "doc-1"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.objectMetadata("attachment", "42");

        assertThat(response.getBody().getData()).containsEntry("documentId", "doc-1");
        verify(vectorStorePort).getMetadata("attachment", "42");
    }

    private static MockMvc jobControllerMockMvc(RagIndexJobService jobService) {
        return MockMvcBuilders.standaloneSetup(new RagIndexJobController(
                        jobService,
                        mock(RagPipelineService.class),
                        null))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .modules(new JavaTimeModule())
                                .build()))
                .build();
    }

    private static class LegacyListJobService implements RagIndexJobService {

        private final RagIndexJob job = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "attachment",
                java.time.Instant.parse("2026-04-26T00:00:00Z"));
        private boolean usedLegacyListJobs;

        @Override
        public RagIndexJob createJob(RagIndexJobCreateRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJob startJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJob retryJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RagIndexJob> getJob(String jobId) {
            return Optional.of(job);
        }

        @Override
        public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
            usedLegacyListJobs = true;
            return new RagIndexJobPage(List.of(job), 1, pageable.offset(), pageable.limit());
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

    private static class CapturingJobService implements RagIndexJobService {

        private final RagIndexJob job;
        private RagIndexJobCreateRequest createdRequest;
        private RagIndexJobSourceRequest createdSourceRequest;
        private RagIndexJobPageRequest pageRequest;
        private RagIndexJobSort sort;

        CapturingJobService() {
            this(RagIndexJobStatus.SUCCEEDED);
        }

        CapturingJobService(RagIndexJobStatus status) {
            this.job = RagIndexJob.pending(
                    "job-1",
                    "attachment",
                    "42",
                    "doc-1",
                    "attachment",
                    java.time.Instant.parse("2026-04-26T00:00:00Z"))
                    .withStatus(status, RagIndexJobStep.COMPLETED, null,
                            java.time.Instant.parse("2026-04-26T00:00:01Z"));
        }

        @Override
        public RagIndexJob createJob(RagIndexJobCreateRequest request) {
            this.createdRequest = request;
            return job;
        }

        @Override
        public RagIndexJob createJob(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
            this.createdRequest = request;
            this.createdSourceRequest = sourceRequest;
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
            return Optional.of(job);
        }

        @Override
        public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
            this.pageRequest = pageable;
            return new RagIndexJobPage(List.of(job), 1, pageable.offset(), pageable.limit());
        }

        @Override
        public RagIndexJobPage listJobs(
                RagIndexJobFilter filter,
                RagIndexJobPageRequest pageable,
                RagIndexJobSort sort) {
            this.pageRequest = pageable;
            this.sort = sort;
            return new RagIndexJobPage(List.of(job), 1, pageable.offset(), pageable.limit());
        }

        @Override
        public List<RagIndexJobLog> getLogs(String jobId) {
            return List.of(new RagIndexJobLog(
                    "log-1",
                    jobId,
                    RagIndexJobLogLevel.INFO,
                    RagIndexJobStep.EXTRACTING,
                    RagIndexJobLogCode.JOB_STARTED,
                    "started",
                    null,
                    java.time.Instant.parse("2026-04-26T00:00:00Z")));
        }

        @Override
        public RagIndexProgressListener progressListener(String jobId) {
            return RagIndexProgressListener.noop();
        }
    }
}
