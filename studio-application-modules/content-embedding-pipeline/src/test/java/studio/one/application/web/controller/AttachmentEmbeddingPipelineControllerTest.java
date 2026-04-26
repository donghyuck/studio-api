package studio.one.application.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.service.AttachmentStructuredRagIndexer;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.ParsedFile;
import studio.one.platform.textract.service.FileContentExtractionService;

class AttachmentEmbeddingPipelineControllerTest {

    private static final String BASE_PATH = "/api/mgmt/attachments";

    private AttachmentService attachmentService;
    private FileContentExtractionService extractionService;
    private EmbeddingPort embeddingPort;
    private RagPipelineService ragPipelineService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        attachmentService = mock(AttachmentService.class);
        extractionService = mock(FileContentExtractionService.class);
        embeddingPort = mock(EmbeddingPort.class);
        ragPipelineService = mock(RagPipelineService.class);

        configureMockMvc(null);
    }

    private void configureMockMvc(AttachmentStructuredRagIndexer structuredRagIndexer) {
        configureMockMvc(structuredRagIndexer, false);
    }

    private void configureMockMvc(AttachmentStructuredRagIndexer structuredRagIndexer, boolean allowClientDebug) {
        configureMockMvc(structuredRagIndexer, allowClientDebug, null);
    }

    private void configureMockMvc(
            AttachmentStructuredRagIndexer structuredRagIndexer,
            boolean allowClientDebug,
            RagIndexJobService ragIndexJobService) {
        AttachmentRagIndexService attachmentRagIndexService = new AttachmentRagIndexService(
                attachmentService,
                provider(extractionService),
                provider(ragPipelineService),
                provider(structuredRagIndexer));
        AttachmentEmbeddingPipelineController controller = new AttachmentEmbeddingPipelineController(
                attachmentService,
                provider(extractionService),
                provider(embeddingPort),
                provider((VectorStorePort) null),
                provider(ragPipelineService),
                provider(ragIndexJobService),
                attachmentRagIndexService,
                provider((I18n) null));
        ReflectionTestUtils.setField(controller, "allowClientDebug", allowClientDebug);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .addPlaceholderValue(PropertyKeys.Features.PREFIX + ".attachment.web.mgmt-base-path", BASE_PATH)
                .build();
    }

    @Test
    void embedReturnsStableApiResponseJsonShape() throws Exception {
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(get(BASE_PATH + "/1/embedding").param("storeVector", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vectors[0].referenceId").value("0"))
                .andExpect(jsonPath("$.data.vectors[0].values[0]").value(0.1d))
                .andExpect(jsonPath("$.data.vectors[0].values[1]").value(0.2d));
    }

    @Test
    void ragSearchBindsRequestAndReturnsStableJsonShape() throws Exception {
        when(ragPipelineService.search(any()))
                .thenReturn(List.of(new RagSearchResult("doc-1", "body", Map.of("topic", "alpha"), 0.9d)));

        mockMvc.perform(post(BASE_PATH + "/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "hello",
                                  "topK": 2,
                                  "objectType": "attachment",
                                  "objectId": "1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.results[0].content").value("body"))
                .andExpect(jsonPath("$.results[0].metadata.topic").value("alpha"))
                .andExpect(jsonPath("$.results[0].score").value(0.9d));

        verify(ragPipelineService).search(argThat((RagSearchRequest request) ->
                request.metadataFilter().hasObjectScope()
                        && "attachment".equals(request.metadataFilter().objectType())
                        && "1".equals(request.metadataFilter().objectId())));
    }

    @Test
    void ragSearchRejectsInvalidRequestBody() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "",
                                  "topK": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ragIndexAddsAttachmentMetadataWithoutOverwritingCallerMetadata() throws Exception {
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "metadata": {
                                    "filename": "caller.txt",
                                    "sourceType": "caller-source",
                                    "indexedAt": "caller-time"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(ragPipelineService).index(argThat((RagIndexRequest request) -> {
            Map<String, Object> metadata = request.metadata();
            return "caller.txt".equals(metadata.get("filename"))
                    && "caller-source".equals(metadata.get("sourceType"))
                    && "caller-time".equals(metadata.get("indexedAt"))
                    && "attachment".equals(metadata.get("objectType"))
                    && "1".equals(metadata.get("objectId"))
                    && Long.valueOf(1L).equals(metadata.get("attachmentId"))
                    && "sample.txt".equals(metadata.get("name"))
                    && "text/plain".equals(metadata.get("contentType"))
                    && Long.valueOf(5L).equals(metadata.get("size"));
        }), any(RagIndexProgressListener.class));
    }

    @Test
    void ragIndexUsesStructuredIndexingWhenAllStructuredBeansAreAvailable() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        ChunkingOrchestrator chunkingOrchestrator = mock(ChunkingOrchestrator.class);
        TextractNormalizedDocumentAdapter adapter = mock(TextractNormalizedDocumentAdapter.class);
        AttachmentStructuredRagIndexer structuredRagIndexer = new DefaultAttachmentStructuredRagIndexer(
                provider(adapter),
                provider(chunkingOrchestrator),
                provider(embeddingPort),
                provider(vectorStore));
        configureMockMvc(structuredRagIndexer, true);

        Attachment attachment = mock(Attachment.class);
        ParsedFile parsedFile = ParsedFile.textOnly(DocumentFormat.TEXT, "structured text", "sample.txt");
        NormalizedDocument normalizedDocument = NormalizedDocument.builder("doc-1")
                .plainText("structured text")
                .metadata(Map.of("parser", "textract"))
                .build();
        Chunk chunk = Chunk.of(
                "doc-1#0",
                "structured text",
                ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, 7)
                        .sourceDocumentId("doc-1")
                        .chunkType(ChunkType.TABLE)
                        .parentChunkId("parent-1")
                        .previousChunkId("prev-1")
                        .nextChunkId("next-1")
                        .objectType("attachment")
                        .objectId("1")
                        .section("Intro")
                        .attributes(Map.of(
                                "headingPath", List.of("Intro", "Table"),
                                "sourceRef", "sample.txt#page=3",
                                "page", 3,
                                "slide", 2,
                                "embeddingModel", "test-embedding"))
                        .build());

        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(15L);
        when(extractionService.parseStructured(any(), any(), any(InputStream.class))).thenReturn(parsedFile);
        when(adapter.adapt("doc-1", parsedFile)).thenReturn(normalizedDocument);
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class))).thenReturn(List.of(chunk));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "doc-1",
                                  "debug": true,
                                  "metadata": {
                                    "category": "manual",
                                    "objectType": "caller-object",
                                    "objectId": "caller-id",
                                    "chunkIndex": 99
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Index-Path", "structured"))
                .andExpect(header().string("X-RAG-Index-Structured", "true"))
                .andExpect(header().string("X-RAG-Index-Parsed-Block-Count", "1"))
                .andExpect(header().string("X-RAG-Index-Chunk-Count", "1"))
                .andExpect(header().string("X-RAG-Index-Vector-Count", "1"))
                .andExpect(header().doesNotExist("X-RAG-Index-Fallback-Reason"));

        verify(extractionService).parseStructured(any(), any(), any(InputStream.class));
        verify(extractionService, never()).extractText(any(), any(), any(InputStream.class));
        verifyNoInteractions(ragPipelineService);
        verify(vectorStore).replaceRecordsByObject(
                argThat("attachment"::equals),
                argThat("1"::equals),
                argThat((List<VectorRecord> records) -> {
                    if (records.size() != 1) {
                        return false;
                    }
                    VectorRecord record = records.get(0);
                    Map<String, Object> metadata = record.toMetadata();
                    return "doc-1#0".equals(record.id())
                            && "doc-1".equals(record.documentId())
                            && "doc-1#0".equals(record.chunkId())
                            && "structured text".equals(record.text())
                            && "parent-1".equals(record.parentChunkId())
                            && "test-embedding".equals(record.embeddingModel())
                            && record.embeddingDimension() == 2
                            && "table".equals(record.chunkType())
                            && "Intro > Table".equals(record.headingPath())
                            && "sample.txt#page=3".equals(record.sourceRef())
                            && Integer.valueOf(3).equals(record.page())
                            && Integer.valueOf(2).equals(record.slide())
                            && record.contentHash() != null
                            && metadata.containsKey("documentId")
                            && "doc-1".equals(metadata.get("documentId"))
                            && "attachment".equals(metadata.get("objectType"))
                            && "1".equals(metadata.get("objectId"))
                            && "manual".equals(metadata.get("category"))
                            && "Intro".equals(metadata.get("section"))
                            && Integer.valueOf(7).equals(metadata.get("chunkOrder"))
                            && Integer.valueOf(7).equals(metadata.get("chunkIndex"))
                            && "table".equals(metadata.get("chunkType"))
                            && "parent-1".equals(metadata.get("parentChunkId"))
                            && "prev-1".equals(metadata.get("previousChunkId"))
                            && "next-1".equals(metadata.get("nextChunkId"))
                            && "Intro > Table".equals(metadata.get("headingPath"))
                            && "sample.txt#page=3".equals(metadata.get("sourceRef"))
                            && Integer.valueOf(3).equals(metadata.get("page"))
                            && Integer.valueOf(2).equals(metadata.get("slide"))
                            && "test-embedding".equals(metadata.get("embeddingModel"))
                            && Integer.valueOf(2).equals(metadata.get("embeddingDimension"))
                            && metadata.containsKey("strategy");
                }));
    }

    @Test
    void ragIndexReopensInputStreamWhenStructuredIndexerFallsBack() throws Exception {
        AttachmentStructuredRagIndexer structuredRagIndexer = (attachment, documentId, objectType, objectId,
                metadata, extractor, inputStream) -> {
            inputStream.readAllBytes();
            return false;
        };
        configureMockMvc(structuredRagIndexer, true);

        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("structured".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(8L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("fallback text");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "debug": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Index-Path", "fallback"))
                .andExpect(header().string("X-RAG-Index-Structured", "false"))
                .andExpect(header().string("X-RAG-Index-Fallback-Reason", "structured_not_handled"))
                .andExpect(header().string("X-RAG-Index-Chunk-Count", "0"));

        verify(attachmentService, times(2)).getInputStream(attachment);
        verify(ragPipelineService).index(argThat((RagIndexRequest request) ->
                "fallback text".equals(request.text())), any(RagIndexProgressListener.class));
    }

    @Test
    void ragIndexAddsJobHeaderWhenJobServiceIsConfigured() throws Exception {
        RagIndexJobService jobService = mock(RagIndexJobService.class);
        configureMockMvc(null, false, jobService);
        when(jobService.createJob(any(RagIndexJobCreateRequest.class), any(RagIndexJobSourceRequest.class)))
                .thenReturn(RagIndexJob.pending(
                        "job-1",
                        "attachment",
                        "1",
                        "1",
                        "attachment",
                        java.time.Instant.parse("2026-04-26T00:00:00Z")));
        when(jobService.progressListener("job-1")).thenReturn(RagIndexProgressListener.noop());
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Job-Id", "job-1"));
    }

    @Test
    void ragIndexDoesNotExposeDiagnosticsHeadersUnlessDebugIsRequested() throws Exception {
        configureMockMvc(null, true);
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("X-RAG-Index-Path"))
                .andExpect(header().doesNotExist("X-RAG-Index-Fallback-Reason"));
    }

    @Test
    void ragIndexDoesNotExposeDiagnosticsHeadersUnlessServerAllowsDebug() throws Exception {
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "debug": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("X-RAG-Index-Path"))
                .andExpect(header().doesNotExist("X-RAG-Index-Fallback-Reason"));
    }

    @Test
    void ragIndexDoesNotEmitFakeCountsWhenCustomStructuredIndexerHasNoDiagnostics() throws Exception {
        AttachmentStructuredRagIndexer structuredRagIndexer = (attachment, documentId, objectType, objectId,
                metadata, extractor, inputStream) -> true;
        configureMockMvc(structuredRagIndexer, true);

        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "debug": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Index-Path", "structured"))
                .andExpect(header().string("X-RAG-Index-Structured", "true"))
                .andExpect(header().doesNotExist("X-RAG-Index-Parsed-Block-Count"))
                .andExpect(header().doesNotExist("X-RAG-Index-Chunk-Count"))
                .andExpect(header().doesNotExist("X-RAG-Index-Vector-Count"));
    }

    @Test
    void structuredIndexerClearsObjectScopeWhenChunkingProducesNoChunks() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        ChunkingOrchestrator chunkingOrchestrator = mock(ChunkingOrchestrator.class);
        TextractNormalizedDocumentAdapter adapter = mock(TextractNormalizedDocumentAdapter.class);
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider(adapter),
                provider(chunkingOrchestrator),
                provider(embeddingPort),
                provider(vectorStore));
        Attachment attachment = mock(Attachment.class);
        ParsedFile parsedFile = ParsedFile.textOnly(DocumentFormat.TEXT, "structured text", "sample.txt");
        NormalizedDocument normalizedDocument = NormalizedDocument.builder("doc-1")
                .plainText("structured text")
                .build();
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(extractionService.parseStructured(any(), any(), any(InputStream.class))).thenReturn(parsedFile);
        when(adapter.adapt("doc-1", parsedFile)).thenReturn(normalizedDocument);
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class))).thenReturn(List.of());

        boolean indexed = indexer.index(
                attachment,
                "doc-1",
                "attachment",
                "1",
                Map.of("category", "manual"),
                extractionService,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        org.assertj.core.api.Assertions.assertThat(indexed).isTrue();
        verify(vectorStore).replaceRecordsByObject("attachment", "1", List.of());
        verifyNoInteractions(embeddingPort);
    }

    @Test
    void structuredIndexerFallsBackWithoutReplacingWhenObjectScopeIsMissing() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        ChunkingOrchestrator chunkingOrchestrator = mock(ChunkingOrchestrator.class);
        TextractNormalizedDocumentAdapter adapter = mock(TextractNormalizedDocumentAdapter.class);
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider(adapter),
                provider(chunkingOrchestrator),
                provider(embeddingPort),
                provider(vectorStore));

        boolean indexed = indexer.index(
                mock(Attachment.class),
                "doc-1",
                null,
                null,
                Map.of(),
                extractionService,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        org.assertj.core.api.Assertions.assertThat(indexed).isFalse();
        verifyNoInteractions(extractionService, vectorStore);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
