package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
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
import studio.one.platform.ai.service.pipeline.RagIndexJobSourceNameResolver;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.RagIndexChunkDto;
import studio.one.platform.ai.web.dto.RagIndexChunkPageResponseDto;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;
import studio.one.platform.ai.web.dto.RagIndexJobDto;
import studio.one.platform.ai.web.dto.RagIndexJobListResponseDto;
import studio.one.platform.ai.web.dto.RagIndexJobLogDto;
import studio.one.platform.web.dto.ApiResponse;

class RagIndexJobControllerTest {

    @Test
    void mutatingJobEndpointsRequireAiRagWritePermission() throws NoSuchMethodException {
        Method createJob = RagIndexJobController.class.getMethod("createJob", RagIndexJobCreateRequestDto.class);
        Method retryJob = RagIndexJobController.class.getMethod("retryJob", String.class);
        Method cancelJob = RagIndexJobController.class.getMethod("cancelJob", String.class);

        assertThat(preAuthorizeValue(createJob)).contains("services:ai_rag','write");
        assertThat(preAuthorizeValue(retryJob)).contains("services:ai_rag','write");
        assertThat(preAuthorizeValue(cancelJob)).contains("services:ai_rag','write");
    }

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
        assertThat(response.getBody().getData().sourceName()).isEqualTo("sample.pdf");
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
                Map.of("attachmentId", "42", "filename", "sample.pdf"),
                List.of("alpha"),
                false));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(jobService.createdRequest.indexRequest()).isNull();
        assertThat(jobService.createdRequest.sourceType()).isEqualTo("attachment");
        assertThat(jobService.createdRequest.documentId()).isEqualTo("doc-1");
        assertThat(jobService.createdRequest.sourceName()).isEqualTo("sample.pdf");
        assertThat(jobService.createdSourceRequest.metadata()).containsEntry("attachmentId", "42");
    }

    @Test
    void createJobSourceNamePriorityUsesRequestThenMetadataThenDocumentId() {
        CapturingJobService explicitJobService = new CapturingJobService();
        RagIndexJobController explicitController = new RagIndexJobController(
                explicitJobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new FixedSourceNameResolver("resolver-source.pdf")));

        explicitController.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null,
                Map.of("sourceName", "metadata-source.pdf", "title", "title.pdf", "filename", "filename.pdf",
                        "fileName", "file-name.pdf", "name", "name.pdf"),
                List.of(),
                false,
                null,
                null,
                null,
                "request-source.pdf"));

        assertThat(explicitJobService.createdRequest.sourceName()).isEqualTo("request-source.pdf");

        CapturingJobService metadataJobService = new CapturingJobService();
        RagIndexJobController metadataController = new RagIndexJobController(
                metadataJobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new FixedSourceNameResolver("resolver-source.pdf")));

        metadataController.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null,
                Map.of("title", "title.pdf", "filename", "filename.pdf",
                        "fileName", "file-name.pdf", "name", "name.pdf"),
                List.of(),
                false));

        assertThat(metadataJobService.createdRequest.sourceName()).isEqualTo("title.pdf");
    }

    @Test
    void createJobUsesSourceNameResolverBeforeDocumentIdFallback() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new FixedSourceNameResolver("attachment-name.pdf")));

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
        assertThat(jobService.createdRequest.sourceName()).isEqualTo("attachment-name.pdf");
        assertThat(jobService.createdSourceRequest.metadata()).containsEntry("attachmentId", "42");
    }

    @Test
    void createTextJobDoesNotUseSourceNameResolver() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new FixedSourceNameResolver("attachment-name.pdf")));

        controller.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                "raw text",
                Map.of(),
                List.of(),
                false));

        assertThat(jobService.createdRequest.indexRequest()).isNotNull();
        assertThat(jobService.createdRequest.sourceName()).isEqualTo("doc-1");
    }

    @Test
    void createJobFallsBackToDocumentIdWhenSourceNameResolverReturnsEmpty() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new EmptySourceNameResolver()));

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

        assertThat(jobService.createdRequest.sourceName()).isEqualTo("42");
    }

    @Test
    void createJobFallsBackToDocumentIdWhenSourceNameResolverThrows() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null,
                Runnable::run,
                200,
                List.of(new ThrowingSourceNameResolver()));

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

        assertThat(jobService.createdRequest.sourceName()).isEqualTo("42");
    }

    @Test
    void createJobClampsLongSourceNameBeforePersistence() {
        CapturingJobService jobService = new CapturingJobService();
        RagIndexJobController controller = new RagIndexJobController(
                jobService,
                mock(RagPipelineService.class),
                null);

        controller.createJob(new RagIndexJobCreateRequestDto(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null,
                Map.of(),
                List.of(),
                false,
                null,
                null,
                null,
                "x".repeat(RagIndexJob.MAX_SOURCE_NAME_LENGTH + 20)));

        assertThat(jobService.createdRequest.sourceName()).hasSize(RagIndexJob.MAX_SOURCE_NAME_LENGTH);
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
        assertThat(jobService.createdRequest.sourceName()).isEqualTo("42");
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
        assertThat(listResponse.getBody().getData().items().get(0).sourceName()).isEqualTo("sample.pdf");
        assertThat(detailResponse.getBody().getData().jobId()).isEqualTo("job-1");
        assertThat(detailResponse.getBody().getData().sourceName()).isEqualTo("sample.pdf");
        assertThat(retryResponse.getStatusCode().value()).isEqualTo(202);
        assertThat(retryResponse.getBody().getData().sourceName()).isEqualTo("sample.pdf");
        assertThat(logsResponse.getBody().getData())
                .extracting(RagIndexJobLogDto::code)
                .containsExactly(RagIndexJobLogCode.JOB_STARTED);
        assertThat(jobService.sort.field()).isEqualTo(RagIndexJobSort.Field.CREATED_AT);
        assertThat(jobService.sort.direction()).isEqualTo(RagIndexJobSort.Direction.DESC);
    }

    @Test
    void dtoFallsBackSourceNameToDocumentIdForLegacyRows() {
        RagIndexJob legacyJob = new RagIndexJob(
                "job-legacy",
                "attachment",
                "42",
                "doc-legacy",
                "attachment",
                null,
                RagIndexJobStatus.PENDING,
                null,
                0,
                0,
                0,
                0,
                null,
                java.time.Instant.parse("2026-04-26T00:00:00Z"),
                null,
                null,
                null);

        assertThat(RagIndexJobDto.from(legacyJob).sourceName()).isEqualTo("doc-legacy");
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
                .andExpect(jsonPath("$.data.items[0].jobId").value("job-1"))
                .andExpect(jsonPath("$.data.items[0].sourceName").value("sample.pdf"));

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
    void cancelAcceptsActiveJob() {
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(RagIndexJobStatus.RUNNING),
                mock(RagPipelineService.class),
                null);

        ResponseEntity<ApiResponse<RagIndexJobDto>> response = controller.cancelJob("job-1");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody().getData().status()).isEqualTo(RagIndexJobStatus.CANCELLED);
        assertThat(response.getBody().getData().sourceName()).isEqualTo("sample.pdf");
    }

    @Test
    void cancelRejectsTerminalJob() {
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(RagIndexJobStatus.SUCCEEDED),
                mock(RagPipelineService.class),
                null);

        assertThatThrownBy(() -> controller.cancelJob("job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void cancelMapsUnsupportedServiceToNotImplemented() {
        RagIndexJobController controller = new RagIndexJobController(
                new LegacyListJobService(),
                mock(RagPipelineService.class),
                null);

        assertThatThrownBy(() -> controller.cancelJob("job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(501));
    }

    @Test
    void cancelMapsServiceRaceRejectionToConflict() {
        RagIndexJobController controller = new RagIndexJobController(
                new RejectingCancelJobService(),
                mock(RagPipelineService.class),
                null);

        assertThatThrownBy(() -> controller.cancelJob("job-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void cancelUnsupportedServiceReturnsProblemDetailsThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new LegacyListJobService());

        mockMvc.perform(post("/api/mgmt/ai/rag/jobs/job-1/cancel"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.detail").value("RAG index job cancel is not supported"));
    }

    @Test
    void cancelRaceRejectionReturnsProblemDetailsThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new RejectingCancelJobService());

        mockMvc.perform(post("/api/mgmt/ai/rag/jobs/job-1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("RAG index job can only be cancelled while active: job-1"));
    }

    @Test
    void missingJobReturnsProblemDetailsThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new MissingJobService());

        mockMvc.perform(post("/api/mgmt/ai/rag/jobs/missing/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("RAG index job not found"));
    }

    @Test
    void jobDetailReturnsNotFoundWhenJobIsMissingThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new MissingJobService());

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("RAG index job not found"));
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
    void objectChunksPageUsesOffsetLimitAndHasMore() {
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(),
                ragPipelineService,
                null);
        when(ragPipelineService.listByObject("attachment", "42", 10, 3))
                .thenReturn(List.of(
                        new RagSearchResult("doc-1", "chunk 1", Map.of(VectorRecord.KEY_CHUNK_ID, "chunk-1"), 1.0d),
                        new RagSearchResult("doc-1", "chunk 2", Map.of(VectorRecord.KEY_CHUNK_ID, "chunk-2"), 1.0d),
                        new RagSearchResult("doc-1", "chunk 3", Map.of(VectorRecord.KEY_CHUNK_ID, "chunk-3"), 1.0d)));

        ResponseEntity<ApiResponse<RagIndexChunkPageResponseDto>> response =
                controller.objectChunksPage("attachment", "42", 10, 2);

        RagIndexChunkPageResponseDto page = response.getBody().getData();
        assertThat(page.offset()).isEqualTo(10);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.returned()).isEqualTo(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).extracting(RagIndexChunkDto::chunkId).containsExactly("chunk-1", "chunk-2");
        verify(ragPipelineService).listByObject("attachment", "42", 10, 3);
    }

    @Test
    void objectChunksPageReportsConfiguredLimitWhenControllerLimitIsLowerThanDefault() {
        RagPipelineService ragPipelineService = mock(RagPipelineService.class);
        RagIndexJobController controller = new RagIndexJobController(
                new CapturingJobService(),
                ragPipelineService,
                null,
                Runnable::run,
                25);
        when(ragPipelineService.listByObject("attachment", "42", 0, 26))
                .thenReturn(java.util.stream.IntStream.rangeClosed(1, 26)
                        .mapToObj(index -> new RagSearchResult(
                                "doc-1",
                                "chunk " + index,
                                Map.of(VectorRecord.KEY_CHUNK_ID, "chunk-" + index),
                                1.0d))
                        .toList());

        ResponseEntity<ApiResponse<RagIndexChunkPageResponseDto>> response =
                controller.objectChunksPage("attachment", "42", 0, 200);

        RagIndexChunkPageResponseDto page = response.getBody().getData();
        assertThat(page.limit()).isEqualTo(25);
        assertThat(page.returned()).isEqualTo(25);
        assertThat(page.hasMore()).isTrue();
        verify(ragPipelineService).listByObject("attachment", "42", 0, 26);
    }

    @Test
    void jobChunksPageReturnsNotFoundWhenJobIsMissingThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new MissingJobService());

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs/missing/chunks/page"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("RAG index job not found"));
    }

    @Test
    void jobChunksReturnNotFoundWhenJobIsMissingThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new MissingJobService());

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs/missing/chunks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("RAG index job not found"));
    }

    @Test
    void jobLogsReturnNotFoundWhenJobIsMissingThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new MissingJobService());

        mockMvc.perform(get("/api/mgmt/ai/rag/jobs/missing/logs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("RAG index job not found"));
    }

    @Test
    void activeRetryReturnsConflictThroughMvc() throws Exception {
        MockMvc mockMvc = jobControllerMockMvc(new CapturingJobService(RagIndexJobStatus.RUNNING));

        mockMvc.perform(post("/api/mgmt/ai/rag/jobs/job-1/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("RAG index job is still active"));
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
                .setControllerAdvice(new AiWebExceptionHandler())
                .build();
    }

    private record FixedSourceNameResolver(String sourceName) implements RagIndexJobSourceNameResolver {

        @Override
        public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
            return true;
        }

        @Override
        public Optional<String> resolveSourceName(
                RagIndexJobCreateRequest request,
                RagIndexJobSourceRequest sourceRequest) {
            return Optional.of(sourceName);
        }
    }

    private static class EmptySourceNameResolver implements RagIndexJobSourceNameResolver {

        @Override
        public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
            return true;
        }

        @Override
        public Optional<String> resolveSourceName(
                RagIndexJobCreateRequest request,
                RagIndexJobSourceRequest sourceRequest) {
            return Optional.empty();
        }
    }

    private static class ThrowingSourceNameResolver implements RagIndexJobSourceNameResolver {

        @Override
        public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
            throw new IllegalStateException("resolver unavailable");
        }

        @Override
        public Optional<String> resolveSourceName(
                RagIndexJobCreateRequest request,
                RagIndexJobSourceRequest sourceRequest) {
            throw new IllegalStateException("resolver unavailable");
        }
    }

    private static String preAuthorizeValue(Method method) {
        return java.util.Arrays.stream(method.getAnnotations())
                .filter(annotation -> "org.springframework.security.access.prepost.PreAuthorize"
                        .equals(annotation.annotationType().getName()))
                .findFirst()
                .map(annotation -> {
                    try {
                        return (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    } catch (ReflectiveOperationException ex) {
                        throw new AssertionError("PreAuthorize value could not be read", ex);
                    }
                })
                .orElseThrow(() -> new AssertionError("PreAuthorize annotation not found"));
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

    private static class RejectingCancelJobService extends LegacyListJobService {

        @Override
        public RagIndexJob cancelJob(String jobId) {
            throw new IllegalStateException("RAG index job can only be cancelled while active: " + jobId);
        }
    }

    private static class MissingJobService extends LegacyListJobService {

        @Override
        public Optional<RagIndexJob> getJob(String jobId) {
            return Optional.empty();
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
                    "sample.pdf",
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
        public RagIndexJob cancelJob(String jobId) {
            if (job.status() != RagIndexJobStatus.PENDING && job.status() != RagIndexJobStatus.RUNNING) {
                throw new IllegalStateException("RAG index job can only be cancelled while active: " + jobId);
            }
            return job.withStatus(
                    RagIndexJobStatus.CANCELLED,
                    job.currentStep(),
                    "RAG index job cancelled",
                    java.time.Instant.parse("2026-04-26T00:00:02Z"));
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
