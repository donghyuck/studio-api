package studio.one.application.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import tools.jackson.databind.json.JsonMapper;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.web.service.AttachmentStructuredRagIndexer;
import studio.one.application.web.service.AttachmentRagIndexService;
import studio.one.application.web.service.AttachmentRagIndexUnavailableException;
import studio.one.application.web.service.DefaultAttachmentStructuredRagIndexer;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingPurpose;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagChunkStageStore;
import studio.one.platform.ai.service.pipeline.InMemoryRagChunkStageStore;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetItem;
import studio.one.platform.chunking.artifact.ChunkSetQualityStatus;
import studio.one.platform.chunking.artifact.ChunkSetStatus;
import studio.one.platform.chunking.artifact.ChunkSetStore;
import studio.one.platform.chunking.artifact.InMemoryChunkSetStore;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.service.TextractNormalizedDocumentAdapter;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

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
                provider(structuredRagIndexer),
                provider((RagChunkStageStore) null));
        AttachmentEmbeddingPipelineController controller = new AttachmentEmbeddingPipelineController(
                attachmentService,
                provider(extractionService),
                provider(embeddingPort),
                provider((VectorStorePort) null),
                provider(ragPipelineService),
                provider(ragIndexJobService),
                provider(RagPipelineOptions.defaults()),
                attachmentRagIndexService,
                provider((I18n) null));
        ReflectionTestUtils.setField(controller, "allowClientDebug", allowClientDebug);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
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
    void ragSearchUsesConfiguredTopKAndMinScoreWhenRequestOmitsThem() throws Exception {
        ArgumentCaptor<RagSearchRequest> captor = ArgumentCaptor.forClass(RagSearchRequest.class);
        AttachmentRagIndexService attachmentRagIndexService = new AttachmentRagIndexService(
                attachmentService,
                provider(extractionService),
                provider(ragPipelineService),
                provider((AttachmentStructuredRagIndexer) null),
                provider((RagChunkStageStore) null));
        AttachmentEmbeddingPipelineController controller = new AttachmentEmbeddingPipelineController(
                attachmentService,
                provider(extractionService),
                provider(embeddingPort),
                provider((VectorStorePort) null),
                provider(ragPipelineService),
                provider((RagIndexJobService) null),
                provider(new RagPipelineOptions(0.7d, 0.3d, 0.42d, 0.15d, true, true, 9, 20, 100)),
                attachmentRagIndexService,
                provider((I18n) null));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc customMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
                .setValidator(validator)
                .addPlaceholderValue(PropertyKeys.Features.PREFIX + ".attachment.web.mgmt-base-path", BASE_PATH)
                .build();
        when(ragPipelineService.search(any())).thenReturn(List.of());

        customMvc.perform(post(BASE_PATH + "/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "hello"
                                }
                                """))
                .andExpect(status().isOk());

        verify(ragPipelineService).search(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(9);
        assertThat(captor.getValue().minScore()).isEqualTo(0.42d);
        assertThat(captor.getValue().requestedTopK()).isNull();
        assertThat(captor.getValue().requestedMinScore()).isNull();
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
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(34L);
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
                    && "2103".equals(metadata.get("parentObjectType"))
                    && "34".equals(metadata.get("parentObjectId"))
                    && "2103".equals(metadata.get("ownerObjectType"))
                    && "34".equals(metadata.get("ownerObjectId"))
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
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(15L);
        when(extractionService.parseStructured(any(), any(), any(InputStream.class))).thenReturn(parsedFile);
        when(adapter.adapt("doc-1", parsedFile)).thenReturn(normalizedDocument);
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class))).thenAnswer(invocation -> {
            NormalizedDocument document = invocation.getArgument(0);
            return List.of(Chunk.of(
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
                            .attributes(mergedChunkAttributes(document.metadata()))
                            .build()));
        });
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "doc-1",
                                  "embeddingProfileId": "request-profile",
                                  "embeddingProvider": "google",
                                  "embeddingModel": "request-model",
                                  "debug": true,
                                  "metadata": {
                                    "category": "manual",
                                    "objectType": "caller-object",
                                    "objectId": "caller-id",
                                    "chunkIndex": 99,
                                    "embeddingProfileId": "metadata-profile",
                                    "embeddingProvider": "metadata-provider",
                                    "embeddingModel": "metadata-model"
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
        verify(embeddingPort).embed(argThat((EmbeddingRequest request) ->
                "request-profile".equals(request.metadata().get("embeddingProfileId"))
                        && "google".equals(request.provider())
                        && "request-model".equals(request.model())));
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
                            && "request-model".equals(record.embeddingModel())
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
                            && "request-profile".equals(metadata.get("embeddingProfileId"))
                            && "google".equals(metadata.get("embeddingProvider"))
                            && "request-model".equals(metadata.get("embeddingModel"))
                            && Integer.valueOf(2).equals(metadata.get("embeddingDimension"))
                            && metadata.containsKey("strategy");
                }));
    }

    @Test
    void ragIndexPersistsDistinctChunkIndexesForStructuredChunks() throws Exception {
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
        ParsedFile parsedFile = ParsedFile.textOnly(DocumentFormat.TEXT, "first\n\nsecond", "sample.txt");
        NormalizedDocument normalizedDocument = NormalizedDocument.builder("doc-1")
                .plainText("first\n\nsecond")
                .metadata(Map.of("parser", "textract"))
                .build();

        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(15L);
        when(extractionService.parseStructured(any(), any(), any(InputStream.class))).thenReturn(parsedFile);
        when(adapter.adapt("doc-1", parsedFile)).thenReturn(normalizedDocument);
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class))).thenAnswer(invocation -> {
            NormalizedDocument document = invocation.getArgument(0);
            return List.of(
                    structuredChunk("doc-1#0", "first", 0, document.metadata()),
                    structuredChunk("doc-1#1", "second", 1, document.metadata()));
        });
        when(embeddingPort.embed(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of(
                        new EmbeddingVector("0", List.of(0.1d, 0.2d)),
                        new EmbeddingVector("1", List.of(0.3d, 0.4d)))));

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": "doc-1",
                                  "debug": true,
                                  "metadata": {
                                    "category": "manual"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Index-Path", "structured"))
                .andExpect(header().string("X-RAG-Index-Chunk-Count", "2"))
                .andExpect(header().string("X-RAG-Index-Vector-Count", "2"));

        verify(vectorStore).replaceRecordsByObject(
                argThat("attachment"::equals),
                argThat("1"::equals),
                argThat((List<VectorRecord> records) ->
                        records.size() == 2
                                && "doc-1#0".equals(records.get(0).chunkId())
                                && "doc-1#1".equals(records.get(1).chunkId())
                                && Integer.valueOf(0).equals(records.get(0).toMetadata().get("chunkIndex"))
                                && Integer.valueOf(1).equals(records.get(1).toMetadata().get("chunkIndex"))
                                && Integer.valueOf(0).equals(records.get(0).toMetadata().get("chunkOrder"))
                                && Integer.valueOf(1).equals(records.get(1).toMetadata().get("chunkOrder"))));
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
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RAG-Job-Id", "job-1"));

        ArgumentCaptor<RagIndexJobCreateRequest> captor = ArgumentCaptor.forClass(RagIndexJobCreateRequest.class);
        verify(jobService).createJob(captor.capture(), any(RagIndexJobSourceRequest.class));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceName()).isEqualTo("sample.txt");
    }

    @Test
    void ragIndexJobUsesAttachmentScopeEvenWhenRequestContainsBusinessObjectScope() throws Exception {
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
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectType": "2103",
                                  "objectId": "2",
                                  "metadata": {
                                    "objectType": "2103",
                                    "objectId": "2"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<RagIndexJobCreateRequest> jobCaptor = ArgumentCaptor.forClass(RagIndexJobCreateRequest.class);
        verify(jobService).createJob(jobCaptor.capture(), any(RagIndexJobSourceRequest.class));
        assertThat(jobCaptor.getValue().objectType()).isEqualTo("attachment");
        assertThat(jobCaptor.getValue().objectId()).isEqualTo("1");
        verify(ragPipelineService).index(argThat((RagIndexRequest request) ->
                        "attachment".equals(request.metadata().get("objectType"))
                                && "1".equals(request.metadata().get("objectId"))
                                && "2103".equals(request.metadata().get("parentObjectType"))
                                && "1".equals(request.metadata().get("parentObjectId"))
                                && "2103".equals(request.metadata().get("ownerObjectType"))
                                && "1".equals(request.metadata().get("ownerObjectId"))),
                any(RagIndexProgressListener.class));
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
    void structuredIndexerEmbedsPreparedChunkSetWithoutReextractingOrRechunking() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        ChunkSetStore chunkSetStore = new InMemoryChunkSetStore();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        chunkSetStore.save(new ChunkSet(
                "cset-test", "attachment", "1", "doc-1", "revision-1", "source-hash",
                "recursive", "strategy-hash", "character", 1000, 100,
                ChunkSetStatus.READY, ChunkSetQualityStatus.VALID, List.of(),
                Map.of("ragIndexEligible", true),
                List.of(new ChunkSetItem(0, "chunk-1", "exact prepared chunk", "content-hash",
                        Map.of("strategy", "recursive", "chunkOrder", 0, "page", 3))),
                now, now));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider((TextractNormalizedDocumentAdapter) null),
                provider((ChunkingOrchestrator) null),
                provider(embeddingPort),
                provider((RagEmbeddingProfileResolver) null),
                provider(vectorStore),
                provider((RagChunkStageStore) null),
                provider(chunkSetStore),
                10,
                10);

        boolean indexed = indexer.index(
                mock(Attachment.class), "doc-1", "attachment", "1",
                Map.of("chunkSetId", "cset-test", "requirePreparedChunks", true),
                extractionService, InputStream.nullInputStream());

        assertThat(indexed).isTrue();
        ArgumentCaptor<EmbeddingRequest> request = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingPort).embed(request.capture());
        assertThat(request.getValue().texts()).containsExactly("exact prepared chunk");
        assertThat(request.getValue().purpose()).isEqualTo(EmbeddingPurpose.INDEX);
        verify(extractionService, never()).parseStructured(any(), any(), any(InputStream.class));
        verifyNoInteractions(ragPipelineService);
    }

    @Test
    void structuredIndexerDoesNotFallbackWhenRequiredChunkSetIsMissing() {
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider((TextractNormalizedDocumentAdapter) null),
                provider((ChunkingOrchestrator) null),
                provider(embeddingPort),
                provider((RagEmbeddingProfileResolver) null),
                provider(mock(VectorStorePort.class)),
                provider((RagChunkStageStore) null),
                provider((ChunkSetStore) new InMemoryChunkSetStore()),
                10,
                10);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> indexer.index(
                        mock(Attachment.class), "doc-1", "attachment", "1",
                        Map.of("chunkSetId", "missing", "requirePreparedChunks", true),
                        extractionService, InputStream.nullInputStream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Prepared ChunkSet was not found");
        verify(extractionService, never()).parseStructured(any(), any(), any(InputStream.class));
    }

    @Test
    void structuredIndexerRetriesTransientEmbeddingFailure() throws Exception {
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
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class)))
                .thenReturn(List.of(structuredChunk("doc-1#0", "first", 0, Map.of())));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenThrow(new IllegalStateException("temporary TEI failure"))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));

        boolean indexed = indexer.index(
                attachment,
                "doc-1",
                "attachment",
                "1",
                Map.of("embeddingProvider", "kure", "embeddingModel", "nlpai-lab/KURE-v1"),
                extractionService,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        org.assertj.core.api.Assertions.assertThat(indexed).isTrue();
        verify(embeddingPort, times(2)).embed(any(EmbeddingRequest.class));
        verify(vectorStore).replaceRecordsByObject(
                argThat("attachment"::equals),
                argThat("1"::equals),
                argThat(records -> records.size() == 1));
    }

    @Test
    void structuredIndexerSkipsAlreadyIndexedLargeBatchChunksWithSameEmbeddingSelection() throws Exception {
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
        when(chunkingOrchestrator.chunk(any(NormalizedDocument.class))).thenReturn(numberedChunks(65));
        when(vectorStore.listByObject("attachment", "1", Integer.MAX_VALUE))
                .thenReturn(existingChunkResults(64));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("64", List.of(0.1d, 0.2d)))));

        boolean indexed = indexer.index(
                attachment,
                "doc-1",
                "attachment",
                "1",
                Map.of("embeddingProvider", "kure", "embeddingModel", "nlpai-lab/KURE-v1"),
                extractionService,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        org.assertj.core.api.Assertions.assertThat(indexed).isTrue();
        verify(vectorStore, never()).deleteByObject("attachment", "1");
        verify(embeddingPort, times(1)).embed(any(EmbeddingRequest.class));
        verify(vectorStore).upsertAll(argThat(records ->
                records.size() == 1
                        && Integer.valueOf(64).equals(records.get(0).toMetadata().get("chunkIndex"))));
    }

    @Test
    void ragIndexResumesFromChunkStageWithoutReadingAttachmentAgain() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        InMemoryRagChunkStageStore chunkStageStore = new InMemoryRagChunkStageStore();
        chunkStageStore.replace("attachment", "1", "doc-1", numberedChunks(3).stream()
                .map(chunk -> new studio.one.platform.ai.service.pipeline.RagChunkStage(
                        "attachment",
                        "1",
                        "doc-1",
                        (Integer) chunk.metadata().toMap().get("chunkOrder"),
                        chunk.id(),
                        chunk.content(),
                        chunk.metadata().toMap(),
                        null))
                .toList());
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider((TextractNormalizedDocumentAdapter) null),
                provider((ChunkingOrchestrator) null),
                provider(embeddingPort),
                provider((RagEmbeddingProfileResolver) null),
                provider(vectorStore),
                provider(chunkStageStore));
        AttachmentRagIndexService service = new AttachmentRagIndexService(
                attachmentService,
                provider(extractionService),
                provider(ragPipelineService),
                provider(indexer),
                provider(chunkStageStore));
        Attachment attachment = mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(1L);
        when(attachment.getName()).thenReturn("manual.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getSize()).thenReturn(100L);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(vectorStore.listByObject("attachment", "1", Integer.MAX_VALUE))
                .thenReturn(existingChunkResults(1));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(
                        new EmbeddingVector("1", List.of(0.1d, 0.2d)),
                        new EmbeddingVector("2", List.of(0.3d, 0.4d)),
                        new EmbeddingVector("3", List.of(0.5d, 0.6d)))));

        service.index(1L, service.command(
                1L,
                "doc-1",
                "attachment",
                "1",
                Map.of("embeddingProvider", "kure", "embeddingModel", "nlpai-lab/KURE-v1"),
                List.of(),
                false,
                null,
                "kure",
                "nlpai-lab/KURE-v1"), RagIndexProgressListener.noop());

        verify(attachmentService, never()).getInputStream(any());
        verifyNoInteractions(extractionService);
        verify(embeddingPort, times(1)).embed(any(EmbeddingRequest.class));
        verify(vectorStore).deleteByObject("attachment", "1");
        verify(vectorStore).upsertAll(argThat(records -> records.size() == 3));
        assertThat(chunkStageStore.findByObject("attachment", "1", "doc-1")).isEmpty();
    }

    @Test
    void ragIndexDoesNotFallbackToAttachmentExtractionWhenChunkStageIsRequired() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        InMemoryRagChunkStageStore chunkStageStore = new InMemoryRagChunkStageStore();
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider((TextractNormalizedDocumentAdapter) null),
                provider((ChunkingOrchestrator) null),
                provider(embeddingPort),
                provider((RagEmbeddingProfileResolver) null),
                provider(vectorStore),
                provider(chunkStageStore));
        AttachmentRagIndexService service = new AttachmentRagIndexService(
                attachmentService,
                provider(extractionService),
                provider(ragPipelineService),
                provider(indexer),
                provider(chunkStageStore));
        Attachment attachment = mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getObjectType()).thenReturn(2103);
        when(attachment.getObjectId()).thenReturn(1L);
        when(attachment.getName()).thenReturn("manual.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getSize()).thenReturn(100L);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.index(1L, service.command(
                1L,
                "doc-1",
                "attachment",
                "1",
                Map.of(
                        AttachmentRagIndexService.METADATA_REQUIRE_RAG_CHUNK_STAGE, true,
                        "embeddingProvider", "kure",
                        "embeddingModel", "nlpai-lab/KURE-v1"),
                List.of(),
                false,
                null,
                "kure",
                "nlpai-lab/KURE-v1"), RagIndexProgressListener.noop()))
                .isInstanceOf(AttachmentRagIndexUnavailableException.class)
                .hasMessage("Required RAG chunk stage was not found");

        verify(attachmentService, never()).getInputStream(any());
        verifyNoInteractions(extractionService, ragPipelineService);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void ragIndexResumedFromChunkStageUsesEmbeddingBatchSizeSeparatelyFromUpsertBatchSize() throws Exception {
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        InMemoryRagChunkStageStore chunkStageStore = new InMemoryRagChunkStageStore();
        chunkStageStore.replace("attachment", "1", "doc-1", numberedChunks(26).stream()
                .map(chunk -> new studio.one.platform.ai.service.pipeline.RagChunkStage(
                        "attachment",
                        "1",
                        "doc-1",
                        (Integer) chunk.metadata().toMap().get("chunkOrder"),
                        chunk.id(),
                        chunk.content(),
                        chunk.metadata().toMap(),
                        null))
                .toList());
        DefaultAttachmentStructuredRagIndexer indexer = new DefaultAttachmentStructuredRagIndexer(
                provider((TextractNormalizedDocumentAdapter) null),
                provider((ChunkingOrchestrator) null),
                provider(embeddingPort),
                provider((RagEmbeddingProfileResolver) null),
                provider(vectorStore),
                provider(chunkStageStore),
                4,
                64);
        Attachment attachment = mock(Attachment.class);
        when(embeddingPort.embed(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            return new EmbeddingResponse(java.util.stream.IntStream.range(0, request.texts().size())
                    .mapToObj(index -> new EmbeddingVector(String.valueOf(index), List.of(0.1d, 0.2d)))
                    .toList());
        });

        boolean indexed = indexer.index(
                attachment,
                "doc-1",
                "attachment",
                "1",
                Map.of("embeddingProvider", "kure", "embeddingModel", "nlpai-lab/KURE-v1"),
                extractionService,
                InputStream.nullInputStream());

        assertThat(indexed).isTrue();
        ArgumentCaptor<EmbeddingRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingPort, times(7)).embed(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(request -> request.texts().size())
                .containsExactly(4, 4, 4, 4, 4, 4, 2);
        verify(vectorStore).deleteByObject("attachment", "1");
        verify(vectorStore).upsertAll(argThat(records -> records.size() == 26));
        assertThat(chunkStageStore.findByObject("attachment", "1", "doc-1")).isEmpty();
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
        when(provider.getIfAvailable(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<T> defaultSupplier = invocation.getArgument(0);
            return value == null ? defaultSupplier.get() : value;
        });
        return provider;
    }

    private Map<String, Object> mergedChunkAttributes(Map<String, Object> metadata) {
        Map<String, Object> attributes = new java.util.HashMap<>(metadata);
        attributes.put("headingPath", List.of("Intro", "Table"));
        attributes.put("sourceRef", "sample.txt#page=3");
        attributes.put("page", 3);
        attributes.put("slide", 2);
        attributes.put("embeddingModel", "test-embedding");
        return attributes;
    }

    private Chunk structuredChunk(String id, String content, int order, Map<String, Object> metadata) {
        return Chunk.of(
                id,
                content,
                ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, order)
                        .sourceDocumentId("doc-1")
                        .chunkType(ChunkType.CHILD)
                        .objectType("attachment")
                        .objectId("1")
                        .attributes(new java.util.HashMap<>(metadata))
                        .build());
    }

    private List<Chunk> numberedChunks(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> structuredChunk("doc-1#" + index, "chunk " + index, index, Map.of()))
                .toList();
    }

    private List<VectorSearchResult> existingChunkResults(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    Map<String, Object> metadata = Map.of(
                            "chunkIndex", index,
                            "embeddingProvider", "kure",
                            "embeddingModel", "nlpai-lab/KURE-v1");
                    return new VectorSearchResult(
                            new VectorDocument("doc-1#" + index, "chunk " + index, metadata, List.of()),
                            1.0d);
                })
                .toList();
    }
}
