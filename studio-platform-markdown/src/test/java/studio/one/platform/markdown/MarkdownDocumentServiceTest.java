package studio.one.platform.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.application.MarkdownDocumentNotFoundException;
import studio.one.platform.markdown.application.MarkdownExtractionRequest;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownConversionPort;
import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.application.port.MarkdownTaskExecutor;
import studio.one.platform.markdown.application.port.MarkdownTransactionOperations;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownPipelineExecutionStatus;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class MarkdownDocumentServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void completesNativeExtractionPromotesCurrentRevisionAndReusesIt() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()), pipeline);

        var first = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));
        var second = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.COMPLETED, first.revision().status());
        assertEquals(first.revision().revisionId(), first.document().currentRevisionId());
        assertTrue(second.reused());
        assertEquals(first.revision().revisionId(), second.revision().revisionId());
        assertEquals(1, pipeline.processed.size());
    }

    @Test
    void treatsEpubMimeTypeAsNativeEpubExtraction() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "book", "application/epub+zip", "epub");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# EPUB", "textract-epub", List.of(), List.of()), MarkdownPipelinePort.noop());

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.COMPLETED, result.revision().status());
        assertEquals("epub", result.revision().sourceFormat());
        assertEquals("TEXTRACT", result.revision().extractorType());
    }

    @Test
    void exposesRunningRevisionBeforeNativeExtractionCompletes() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "book.epub", "application/epub+zip", "epub");
        DeferredTaskExecutor tasks = new DeferredTaskExecutor();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# EPUB", "textract-epub", List.of(), List.of()),
                MarkdownPipelinePort.noop(), tasks);

        var created = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.RUNNING, created.revision().status());
        assertEquals(created.document().documentId(),
                service.getDocumentBySourceAttachmentId(1L).documentId());
        assertEquals(MarkdownRevisionStatus.RUNNING,
                service.getRevisions(created.document().documentId()).get(0).status());

        tasks.runNext();

        assertEquals(MarkdownRevisionStatus.COMPLETED,
                service.getRevisions(created.document().documentId()).get(0).status());
    }

    @Test
    void resumesFailedPipelineFromRecordedStage() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        DeferredTaskExecutor tasks = new DeferredTaskExecutor();
        ResumablePipeline pipeline = new ResumablePipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()), pipeline, tasks);

        var created = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));
        tasks.runNext();
        tasks.runNext();

        MarkdownPipelineExecution failed = service.getPipelineExecution(created.document().documentId());
        assertEquals(MarkdownPipelineExecutionStatus.FAILED, failed.status());
        assertEquals(MarkdownPipelineStage.RAG_INDEX, failed.currentStage());

        pipeline.failRag = false;
        var resumed = service.resume(created.document().documentId(), null);
        tasks.runNext();

        assertEquals(MarkdownPipelineStage.RAG_INDEX, resumed.resumedFrom());
        assertEquals(List.of(MarkdownPipelineStage.CHUNKING, MarkdownPipelineStage.RAG_INDEX),
                pipeline.startedFrom);
        assertEquals(MarkdownPipelineExecutionStatus.COMPLETED,
                service.getPipelineExecution(created.document().documentId()).status());
    }

    @Test
    void reindexesRagWithNewEmbeddingProfileWithoutExtractingMarkdownAgain() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        int[] extractionCount = {0};
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> {
                    extractionCount[0]++;
                    return new MarkdownNativeExtractorPort.NativeExtraction(
                            "# Hello",
                            "textract-1",
                            List.of(new MarkdownLocator(
                                    "mloc-source", revisionId, "SECTION", 1, "Hello",
                                    0, 7, null, null)),
                            List.of(new MarkdownResource(
                                    "mres-source", revisionId, "IMAGE", "cover.png", 9L, null)));
                },
                pipeline);
        var created = service.create(new MarkdownExtractionRequest(
                1L,
                new MarkdownPipelineOptions(false, false, false),
                false,
                "tester"));

        var reindexed = service.reindexRag(
                created.document().documentId(),
                "retrieval-ko-kure",
                null,
                null,
                null,
                false);

        assertEquals(1, extractionCount[0]);
        assertEquals(2, service.getRevisions(created.document().documentId()).size());
        assertEquals(created.revision().markdownText(), reindexed.revision().markdownText());
        assertEquals(reindexed.revision().revisionId(), reindexed.document().currentRevisionId());
        assertEquals(MarkdownPipelineStage.CHUNKING, reindexed.resumedFrom());
        MarkdownPipelineOptions options = new ObjectMapper().readValue(
                reindexed.revision().optionsJson(), MarkdownPipelineOptions.class);
        assertEquals("retrieval-ko-kure", options.embeddingProfileId());
        assertTrue(options.runChunking());
        assertTrue(options.runRagIndex());
        assertEquals(1, repository.findLocators(reindexed.revision().revisionId()).size());
        assertEquals(1, repository.findResources(reindexed.revision().revisionId()).size());
        assertFalse(repository.findLocators(reindexed.revision().revisionId()).get(0).locatorId()
                .equals("mloc-source"));
        assertFalse(repository.findResources(reindexed.revision().revisionId()).get(0).resourceId()
                .equals("mres-source"));
        assertEquals(1, pipeline.processed.size());
        assertEquals("retrieval-ko-kure", pipeline.options.embeddingProfileId());
    }

    @Test
    void returnsPersistentCompletedPipelineStateWhenNoDownstreamStageWasRequested() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()), MarkdownPipelinePort.noop());

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        MarkdownPipelineExecution pipeline = service.getPipelineExecution(result.document().documentId());

        assertEquals(MarkdownPipelineExecutionStatus.COMPLETED, pipeline.status());
        assertEquals(MarkdownPipelineStage.COMPLETED, pipeline.currentStage());
    }

    @Test
    void returnsUnknownPipelineStateForLegacyCompletedRevisionWithoutExecutionHistory() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()), MarkdownPipelinePort.noop());
        var result = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));
        repository.pipelineExecutions.clear();

        MarkdownPipelineExecution pipeline = service.getPipelineExecution(result.document().documentId());

        assertEquals(MarkdownPipelineExecutionStatus.UNKNOWN, pipeline.status());
        assertEquals("PIPELINE_HISTORY_UNAVAILABLE", pipeline.errorCode());
    }

    @Test
    void missingAttachmentMarkdownDocumentIsNotFound() {
        MarkdownDocumentService service = service(new InMemoryRepository(), new SourcePort(),
                (source, revisionId) -> {
                    throw new AssertionError();
                }, MarkdownPipelinePort.noop());

        assertThrows(MarkdownDocumentNotFoundException.class,
                () -> service.getDocumentBySourceAttachmentId(999L));
    }

    @Test
    void failedForcedRevisionDoesNotReplaceCurrentRevision() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        ToggleExtractor extractor = new ToggleExtractor();
        MarkdownDocumentService service = service(repository, sources, extractor, MarkdownPipelinePort.noop());

        var completed = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        extractor.fail = true;
        var failed = service.create(new MarkdownExtractionRequest(1L, false, false, false, true, "tester"));

        assertEquals(MarkdownRevisionStatus.FAILED, failed.revision().status());
        assertEquals(completed.revision().revisionId(),
                service.getDocument(completed.document().documentId()).currentRevisionId());
        assertEquals(2, service.getRevisions(completed.document().documentId()).size());
    }

    @Test
    void completesPandocRevisionFromResultAttachment() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        sources.add(2L, "sample.md", "text/markdown", "# Converted");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> {
                    throw new AssertionError("native extractor must not run");
                }, MarkdownPipelinePort.noop());

        var created = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        service.onConversionCompleted(created.revision().documentConvertJobId(), 2L, 1L);
        MarkdownRevision completed = service.getRevisions(created.document().documentId()).get(0);

        assertEquals(MarkdownRevisionStatus.COMPLETED, completed.status());
        assertEquals(2L, completed.resultAttachmentId());
        assertEquals("# Converted", completed.markdownText());
        assertEquals(completed.revisionId(), service.getDocument(completed.documentId()).currentRevisionId());
        assertFalse(service.getLocators(completed.documentId()).isEmpty());
    }

    @Test
    void persistsPipelineOptionsAndExpandsSkillDependencies() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()), pipeline);
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, true,
                "fixed-size", 400, 40, "token",
                null, "google", "gemini-embedding-001", 768);

        var result = service.create(new MarkdownExtractionRequest(1L, options, false, "tester"));
        Map<String, Object> stored = new ObjectMapper().readValue(
                result.revision().optionsJson(), new TypeReference<>() {
                });

        assertTrue(pipeline.options.runChunking());
        assertTrue(pipeline.options.runRagIndex());
        assertTrue(pipeline.options.runSkillExtraction());
        assertEquals("fixed-size", stored.get("chunkingStrategy"));
        assertEquals(768, stored.get("embeddingDimension"));
    }

    private MarkdownDocumentService service(InMemoryRepository repository, SourcePort sources,
            MarkdownNativeExtractorPort extractor, MarkdownPipelinePort pipeline) {
        return service(repository, sources, extractor, pipeline, MarkdownTaskExecutor.direct());
    }

    private MarkdownDocumentService service(InMemoryRepository repository, SourcePort sources,
            MarkdownNativeExtractorPort extractor, MarkdownPipelinePort pipeline, MarkdownTaskExecutor taskExecutor) {
        MarkdownConversionPort conversion = new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                return new ConversionSubmission(jobId, "RUNNING", null, null);
            }

            @Override
            public void cancel(String jobId) {
            }
        };
        return new MarkdownDocumentService(repository, sources, extractor, conversion, pipeline,
                taskExecutor, MarkdownTransactionOperations.direct(),
                new ObjectMapper(), CLOCK, "pandoc-3");
    }

    private static final class DeferredTaskExecutor implements MarkdownTaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void executeAfterCommit(Runnable task) {
            tasks.add(task);
        }

        private void runNext() {
            tasks.remove(0).run();
        }
    }

    private static final class ToggleExtractor implements MarkdownNativeExtractorPort {
        private boolean fail;

        @Override
        public NativeExtraction extract(MarkdownSourcePort.MarkdownSource source, String revisionId) {
            if (fail) {
                throw new IllegalStateException("failed");
            }
            return new NativeExtraction("# Hello", "textract-1", List.of(), List.of());
        }
    }

    private static final class CapturingPipeline implements MarkdownPipelinePort {
        private final List<MarkdownRevision> processed = new ArrayList<>();
        private MarkdownPipelineOptions options;

        @Override
        public void process(MarkdownRevision revision, boolean runChunking, boolean runRagIndex,
                boolean runSkillExtraction) {
            processed.add(revision);
            assertTrue(runChunking);
            assertTrue(runRagIndex);
        }

        @Override
        public void process(MarkdownRevision revision, MarkdownPipelineOptions options) {
            this.options = options;
            process(revision, options.runChunking(), options.runRagIndex(), options.runSkillExtraction());
        }
    }

    private static final class ResumablePipeline implements MarkdownPipelinePort {
        private final List<MarkdownPipelineStage> startedFrom = new ArrayList<>();
        private boolean failRag = true;

        @Override
        public void process(MarkdownRevision revision, boolean runChunking, boolean runRagIndex,
                boolean runSkillExtraction) {
        }

        @Override
        public void process(MarkdownRevision revision, MarkdownPipelineOptions options,
                MarkdownPipelineStage fromStage, java.util.function.Consumer<MarkdownPipelineStage> completed) {
            startedFrom.add(fromStage);
            if (fromStage.ordinal() <= MarkdownPipelineStage.CHUNKING.ordinal()) {
                completed.accept(MarkdownPipelineStage.CHUNKING);
            }
            if (fromStage.ordinal() <= MarkdownPipelineStage.RAG_INDEX.ordinal()) {
                if (failRag) {
                    throw new IllegalStateException("rag failed");
                }
                completed.accept(MarkdownPipelineStage.RAG_INDEX);
            }
        }
    }

    private static final class SourcePort implements MarkdownSourcePort {
        private final Map<Long, MarkdownSource> sources = new HashMap<>();

        private void add(long id, String name, String contentType, String content) {
            sources.put(id, new MarkdownSource(id, name, contentType, "2001", "42",
                    content.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public MarkdownSource load(long attachmentId) {
            MarkdownSource source = sources.get(attachmentId);
            assertNotNull(source);
            return source;
        }
    }

    private static final class InMemoryRepository implements MarkdownRepository {
        private final Map<String, MarkdownDocument> documents = new HashMap<>();
        private final Map<String, MarkdownRevision> revisions = new HashMap<>();
        private final Map<String, List<MarkdownLocator>> locators = new HashMap<>();
        private final Map<String, List<MarkdownResource>> resources = new HashMap<>();
        private final Map<String, MarkdownPipelineExecution> pipelineExecutions = new HashMap<>();

        @Override
        public MarkdownDocument saveDocument(MarkdownDocument document) {
            documents.put(document.documentId(), document);
            return document;
        }

        @Override
        public MarkdownRevision saveRevision(MarkdownRevision revision) {
            revisions.put(revision.revisionId(), revision);
            return revision;
        }

        @Override
        public MarkdownPipelineExecution savePipelineExecution(MarkdownPipelineExecution execution) {
            pipelineExecutions.put(execution.revisionId(), execution);
            return execution;
        }

        @Override
        public Optional<MarkdownDocument> findDocument(String documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public Optional<MarkdownDocument> findDocumentBySourceAttachmentId(long sourceAttachmentId) {
            return documents.values().stream()
                    .filter(document -> document.sourceAttachmentId() == sourceAttachmentId)
                    .findFirst();
        }

        @Override
        public Optional<MarkdownRevision> findRevision(String revisionId) {
            return Optional.ofNullable(revisions.get(revisionId));
        }

        @Override
        public Optional<MarkdownRevision> findRevisionByConvertJobId(String convertJobId) {
            return revisions.values().stream()
                    .filter(revision -> convertJobId.equals(revision.documentConvertJobId()))
                    .findFirst();
        }

        @Override
        public Optional<MarkdownPipelineExecution> findPipelineExecution(String revisionId) {
            return Optional.ofNullable(pipelineExecutions.get(revisionId));
        }

        @Override
        public Optional<MarkdownRevision> findActiveRevisionBySourceAttachmentId(long sourceAttachmentId) {
            return revisions.values().stream()
                    .filter(revision -> revision.sourceAttachmentId() == sourceAttachmentId)
                    .filter(revision -> !revision.status().terminal())
                    .findFirst();
        }

        @Override
        public Optional<MarkdownRevision> findReusableRevision(long sourceAttachmentId, String sourceContentHash,
                String extractorType, String extractorVersion, String optionsHash) {
            return revisions.values().stream()
                    .filter(revision -> revision.sourceAttachmentId() == sourceAttachmentId)
                    .filter(revision -> revision.status() == MarkdownRevisionStatus.COMPLETED)
                    .filter(revision -> sourceContentHash.equals(revision.sourceContentHash()))
                    .filter(revision -> extractorType.equals(revision.extractorType()))
                    .filter(revision -> extractorVersion.equals(revision.extractorVersion()))
                    .filter(revision -> optionsHash.equals(revision.optionsHash()))
                    .findFirst();
        }

        @Override
        public List<MarkdownRevision> findRevisions(String documentId) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.documentId()))
                    .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                    .toList();
        }

        @Override
        public void replaceLocators(String revisionId, List<MarkdownLocator> values) {
            locators.put(revisionId, List.copyOf(values));
        }

        @Override
        public void replaceResources(String revisionId, List<MarkdownResource> values) {
            resources.put(revisionId, List.copyOf(values));
        }

        @Override
        public List<MarkdownLocator> findLocators(String revisionId) {
            return locators.getOrDefault(revisionId, List.of());
        }

        @Override
        public List<MarkdownResource> findResources(String revisionId) {
            return resources.getOrDefault(revisionId, List.of());
        }
    }
}
