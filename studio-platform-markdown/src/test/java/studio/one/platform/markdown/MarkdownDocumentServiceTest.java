package studio.one.platform.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import studio.one.platform.markdown.application.MarkdownDocumentService;
import studio.one.platform.markdown.application.MarkdownDocumentNotFoundException;
import studio.one.platform.markdown.application.MarkdownContentUnavailableException;
import studio.one.platform.markdown.application.MarkdownExtractionRequest;
import studio.one.platform.markdown.application.MarkdownPagePreviewBounds;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyOptions;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergeBatchApplyResult;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreview;
import studio.one.platform.markdown.application.MarkdownIdeaBlockMergePreviewOptions;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelinePlan;
import studio.one.platform.markdown.application.MarkdownResumeResult;
import studio.one.platform.markdown.application.MarkdownResumeOptions;
import studio.one.platform.markdown.application.port.MarkdownConversionPort;
import studio.one.platform.markdown.application.port.MarkdownNativeExtractorPort;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownPagePreviewPort;
import studio.one.platform.markdown.application.port.MarkdownPipelinePort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;
import studio.one.platform.markdown.application.port.MarkdownTaskExecutor;
import studio.one.platform.markdown.application.port.MarkdownTransactionOperations;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownExtractPart;
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
    void reextractInheritsLastExplicitQualityOptionsWhenClientOmitsThem() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "math-textbook.pdf", "application/pdf", "%PDF-test");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Math", "textract-1", List.of(), List.of()), MarkdownPipelinePort.noop());
        MarkdownPipelineOptions qualityOptions = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null,
                null, null, null,
                null, null, null, null,
                false, null, false,
                null, null, null,
                true, "kor+eng", "FORCE", true)
                .withMetadataOptions("BOOK", "REQUIRED");

        var initial = service.create(new MarkdownExtractionRequest(1L, qualityOptions, true, "tester"));
        var reextracted = service.reextract(
                initial.document().documentId(), new MarkdownPipelineOptions(false, false, false), true, "tester");
        Map<String, Object> stored = new ObjectMapper().readValue(
                reextracted.revision().optionsJson(), new TypeReference<>() {});

        assertEquals(Boolean.TRUE, stored.get("ocrRequired"));
        assertEquals("kor+eng", stored.get("ocrLanguage"));
        assertEquals("FORCE", stored.get("ocrMode"));
        assertEquals(Boolean.TRUE, stored.get("mathVisionCorrection"));
        assertEquals("BOOK", stored.get("requestedDocumentSemanticType"));
        assertEquals("REQUIRED", stored.get("metadataEnrichmentMode"));
    }

    @Test
    void rewritesLogicalImageReferencesAndCachesPdfPagePreview() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "%PDF-test");
        int[] renders = {0};
        MarkdownPagePreviewPort previews = (source, page, bounds) -> {
            renders[0]++;
            return new byte[] {1, 2, 3};
        };
        var cacheDirectory = Files.createTempDirectory("markdown-preview-test");
        MarkdownDocumentService service = new MarkdownDocumentService(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "![diagram](page[2]/image[0])", "textract-1", List.of(), List.of()),
                conversion(), MarkdownNormalizationPort.noop(), MarkdownPipelinePort.noop(),
                MarkdownTaskExecutor.direct(), MarkdownTransactionOperations.direct(), new ObjectMapper(), CLOCK,
                "pandoc-3", Set.of("docx", "html"), true, cacheDirectory, previews, "/markdown-api");

        var result = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));

        assertTrue(result.revision().markdownText()
                .contains("](/markdown-api/" + result.document().documentId() + "/pages/2/preview)"));
        assertTrue(Files.readString(service.getCurrentMarkdown(result.document().documentId()).path())
                .contains("](/markdown-api/" + result.document().documentId() + "/pages/2/preview)"));
        var first = service.getPagePreview(result.document().documentId(), 2,
                new MarkdownPagePreviewBounds(1, 2, 10, 20));
        var second = service.getPagePreview(result.document().documentId(), 2,
                new MarkdownPagePreviewBounds(1, 2, 10, 20));

        assertEquals(1, renders[0]);
        assertEquals(first.path(), second.path());
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(first.path()));
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
    void usesConfiguredPandocFormats() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "book", "application/epub+zip", "epub");
        int[] nativeExtractions = {0};
        List<String> submittedFormats = new ArrayList<>();
        MarkdownConversionPort conversion = new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                submittedFormats.add(sourceFormat);
                return new ConversionSubmission(jobId, "RUNNING", null, null);
            }

            @Override
            public void cancel(String jobId) {
            }
        };
        MarkdownDocumentService service = service(repository, sources, (source, revisionId) -> {
            nativeExtractions[0]++;
            return new MarkdownNativeExtractorPort.NativeExtraction("# EPUB", "textract-epub", List.of(), List.of());
        }, conversion, MarkdownPipelinePort.noop(), MarkdownTaskExecutor.direct(), Set.of("docx", "html", "epub"),
                true);

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.RUNNING, result.revision().status());
        assertEquals("PANDOC", result.revision().extractorType());
        assertEquals("epub", result.revision().sourceFormat());
        assertEquals(List.of("epub"), submittedFormats);
        assertEquals(0, nativeExtractions[0]);
    }

    @Test
    void fallsBackToNativeWhenPandocSubmissionFails() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx");
        MarkdownConversionPort conversion = new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                return new ConversionSubmission(jobId, "FAILED", "PANDOC_FAILED", "Pandoc failed");
            }

            @Override
            public void cancel(String jobId) {
            }
        };
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-docx", List.of(), List.of()),
                conversion, MarkdownPipelinePort.noop(), MarkdownTaskExecutor.direct(), Set.of("docx", "html"),
                true);

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.COMPLETED, result.revision().status());
        assertEquals("TEXTRACT", result.revision().extractorType());
        assertEquals("native", result.revision().extractorVersion());
        assertEquals("# Native", result.revision().markdownText());
    }

    @Test
    void fallsBackToNativeWhenPandocConversionFailsLater() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-docx", List.of(), List.of()),
                MarkdownPipelinePort.noop());

        var created = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        service.onConversionFailed(created.revision().documentConvertJobId(), 1L, "PANDOC_FAILED", "Pandoc failed");

        MarkdownRevision revision = service.getRevisions(created.document().documentId()).get(0);
        assertEquals(MarkdownRevisionStatus.COMPLETED, revision.status());
        assertEquals("TEXTRACT", revision.extractorType());
        assertEquals("# Native", revision.markdownText());
    }

    @Test
    void keepsPandocFailureFallbackIdempotentWhenCallbackWinsSubmissionRace() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx");
        DeferredTaskExecutor tasks = new DeferredTaskExecutor();
        MarkdownDocumentService[] serviceRef = new MarkdownDocumentService[1];
        MarkdownConversionPort conversion = new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                serviceRef[0].onConversionFailed(jobId, sourceAttachmentId, "PANDOC_TIMEOUT", "Pandoc timed out");
                throw new IllegalStateException("Pandoc submission timed out");
            }

            @Override
            public void cancel(String jobId) {
            }
        };
        serviceRef[0] = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-docx", List.of(), List.of()),
                conversion, MarkdownPipelinePort.noop(), tasks, Set.of("docx", "html"), true);

        var created = serviceRef[0].create(
                new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.RUNNING, created.revision().status());
        assertEquals("TEXTRACT", created.revision().extractorType());
        assertEquals(1, tasks.tasks.size());

        tasks.runNext();

        MarkdownRevision revision = serviceRef[0].getRevisions(created.document().documentId()).get(0);
        assertEquals(MarkdownRevisionStatus.COMPLETED, revision.status());
        assertEquals("# Native", revision.markdownText());
    }

    @Test
    void doesNotOverwriteNativeFallbackWhenCallbackCompletesBeforePandocSubmissionReturns() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx");
        DeferredTaskExecutor tasks = new DeferredTaskExecutor();
        MarkdownDocumentService[] serviceRef = new MarkdownDocumentService[1];
        MarkdownConversionPort conversion = new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                serviceRef[0].onConversionFailed(jobId, sourceAttachmentId, "PANDOC_FAILED", "Pandoc failed");
                return new ConversionSubmission(jobId, "RUNNING", null, null);
            }

            @Override
            public void cancel(String jobId) {
            }
        };
        serviceRef[0] = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-docx", List.of(), List.of()),
                conversion, MarkdownPipelinePort.noop(), tasks, Set.of("docx", "html"), true);

        var created = serviceRef[0].create(
                new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.RUNNING, created.revision().status());
        assertEquals("TEXTRACT", created.revision().extractorType());
        assertEquals(1, tasks.tasks.size());

        tasks.runNext();

        MarkdownRevision revision = serviceRef[0].getRevisions(created.document().documentId()).get(0);
        assertEquals(MarkdownRevisionStatus.COMPLETED, revision.status());
        assertEquals("TEXTRACT", revision.extractorType());
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
    void blankNativeExtractionFailsRevisionWithoutRunningPipeline() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "scan.pdf", "application/pdf", "pdf");
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "   \n\t", "textract-1", List.of(), List.of()),
                pipeline);

        var result = service.create(new MarkdownExtractionRequest(1L, true, true, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.FAILED, result.revision().status());
        assertEquals("NO_TEXT_EXTRACTED", result.revision().errorCode());
        assertEquals("Extracted markdown text is blank", result.revision().errorMessage());
        assertEquals(null, service.getDocument(result.document().documentId()).currentRevisionId());
        assertEquals(0, pipeline.processed.size());
    }

    @Test
    void doesNotReuseCompletedRevisionWithBlankMarkdown() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "scan.pdf", "application/pdf", "pdf");
        int[] extractionCount = {0};
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> {
                    extractionCount[0]++;
                    return new MarkdownNativeExtractorPort.NativeExtraction(
                            "# Extracted", "textract-1", List.of(), List.of());
                },
                MarkdownPipelinePort.noop());

        var first = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        MarkdownRevision blankCompleted = new MarkdownRevision(
                first.revision().revisionId(),
                first.revision().documentId(),
                first.revision().sourceAttachmentId(),
                first.revision().resultAttachmentId(),
                first.revision().documentConvertJobId(),
                first.revision().extractorType(),
                first.revision().extractorVersion(),
                first.revision().optionsJson(),
                first.revision().optionsHash(),
                first.revision().sourceContentHash(),
                null,
                " ",
                first.revision().sourceFileName(),
                first.revision().sourceFormat(),
                first.revision().sourceObjectType(),
                first.revision().sourceObjectId(),
                MarkdownRevisionStatus.COMPLETED,
                null,
                null,
                first.revision().createdAt(),
                first.revision().startedAt(),
                first.revision().completedAt(),
                first.revision().updatedAt());
        repository.saveRevision(blankCompleted);

        var second = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertFalse(second.reused());
        assertEquals(2, extractionCount[0]);
        assertTrue(second.revision().markdownText().contains("# Extracted"));
        assertFalse(first.revision().revisionId().equals(second.revision().revisionId()));
    }

    @Test
    void reindexRagRejectsCompletedRevisionWithBlankMarkdown() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "scan.pdf", "application/pdf", "pdf");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Extracted", "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop());

        var created = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        repository.saveRevision(new MarkdownRevision(
                created.revision().revisionId(),
                created.revision().documentId(),
                created.revision().sourceAttachmentId(),
                created.revision().resultAttachmentId(),
                created.revision().documentConvertJobId(),
                created.revision().extractorType(),
                created.revision().extractorVersion(),
                created.revision().optionsJson(),
                created.revision().optionsHash(),
                created.revision().sourceContentHash(),
                null,
                "",
                created.revision().sourceFileName(),
                created.revision().sourceFormat(),
                created.revision().sourceObjectType(),
                created.revision().sourceObjectId(),
                MarkdownRevisionStatus.COMPLETED,
                null,
                null,
                created.revision().createdAt(),
                created.revision().startedAt(),
                created.revision().completedAt(),
                created.revision().updatedAt()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.reindexRag(created.document().documentId(), "retrieval-ko-kure", null, null, null, false));

        assertEquals("Completed Markdown revision with text is required for RAG reindex", error.getMessage());
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
        assertEquals(List.of(MarkdownPipelineStage.METADATA_ENRICHMENT, MarkdownPipelineStage.RAG_INDEX),
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
                profiledOptions(false, false),
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
        assertEquals("MATH_TEXTBOOK", options.requestedDocumentProfile());
        assertEquals("MATH_TEXTBOOK", options.resolvedDocumentProfile());
        assertNotNull(options.documentProfileVersion());
        assertEquals(Boolean.TRUE, options.ocrRequired());
        assertEquals("kor", options.ocrLanguage());
        assertEquals("FORCE", options.ocrMode());
        assertEquals(Boolean.TRUE, options.mathVisionCorrection());
        assertEquals(1, repository.findLocators(reindexed.revision().revisionId()).size());
        assertEquals(1, repository.findResources(reindexed.revision().revisionId()).size());
        assertFalse(repository.findLocators(reindexed.revision().revisionId()).get(0).locatorId()
                .equals("mloc-source"));
        assertFalse(repository.findResources(reindexed.revision().revisionId()).get(0).resourceId()
                .equals("mres-source"));
        assertEquals(2, pipeline.processed.size());
        assertEquals("retrieval-ko-kure", pipeline.options.embeddingProfileId());
    }

    @Test
    void resumeOverridesRagKeywordAndSkillEmbeddingOptions() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()),
                pipeline);
        var created = service.create(new MarkdownExtractionRequest(
                1L, profiledOptions(false, false), false, "tester"));

        service.resumeWithOptions(created.document().documentId(), new MarkdownResumeOptions(
                MarkdownPipelineStage.RAG_INDEX,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                "retrieval-ko-kure",
                null,
                null,
                null,
                true,
                "llm",
                true,
                "kure",
                "nlpai-lab/KURE-v1",
                1024));

        assertTrue(pipeline.options.useLlmKeywordExtraction());
        assertEquals("llm", pipeline.options.skillExtractionMode());
        assertTrue(pipeline.options.generateSkillEmbeddings());
        assertEquals("kure", pipeline.options.skillEmbeddingProvider());
        assertEquals("nlpai-lab/KURE-v1", pipeline.options.skillEmbeddingModel());
        assertEquals(1024, pipeline.options.skillEmbeddingDimension());
        assertEquals("MATH_TEXTBOOK", pipeline.options.requestedDocumentProfile());
        assertEquals("MATH_TEXTBOOK", pipeline.options.resolvedDocumentProfile());
        assertNotNull(pipeline.options.documentProfileVersion());
        assertEquals(Boolean.TRUE, pipeline.options.ocrRequired());
        assertEquals("kor", pipeline.options.ocrLanguage());
        assertEquals("FORCE", pipeline.options.ocrMode());
        assertEquals(Boolean.TRUE, pipeline.options.mathVisionCorrection());
    }

    @Test
    void resumeCompletesRunningNativeRevisionFromCompletedExtractParts() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "pdf");
        CapturingPipeline pipeline = new CapturingPipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> {
                    throw new IllegalStateException("native extractor should not be called");
                },
                pipeline);
        String documentId = "mdoc-stale";
        String revisionId = "mrev-stale";
        repository.saveDocument(new MarkdownDocument(documentId, 1L, null, CLOCK.instant(), CLOCK.instant()));
        repository.saveRevision(new MarkdownRevision(
                revisionId,
                documentId,
                1L,
                null,
                null,
                "TEXTRACT",
                "native",
                new ObjectMapper().writeValueAsString(MarkdownPipelineOptions.none()),
                "options-hash",
                "source-hash",
                null,
                null,
                "sample.pdf",
                "pdf",
                null,
                null,
                MarkdownRevisionStatus.RUNNING,
                null,
                null,
                CLOCK.instant(),
                CLOCK.instant(),
                null,
                CLOCK.instant()));
        repository.saveExtractPart(extractPart(revisionId, 101, 200, "## Page 101"));
        repository.saveExtractPart(extractPart(revisionId, 1, 100, "# Page 1"));

        MarkdownResumeResult result = service.resumeWithOptions(documentId, null);

        assertEquals("COMPLETED", result.resumedPhase());
        assertEquals(MarkdownRevisionStatus.COMPLETED, result.revision().status());
        assertEquals("# Page 1\n\n## Page 101", result.revision().markdownText());
        assertEquals(revisionId, result.document().currentRevisionId());
        assertEquals(MarkdownPipelineExecutionStatus.COMPLETED, result.pipeline().status());
        assertEquals(1, pipeline.processed.size());
    }

    @Test
    void getsCurrentAndRevisionMarkdownContent() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "hello");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()),
                new CapturingPipeline());
        var created = service.create(new MarkdownExtractionRequest(
                1L, MarkdownPipelineOptions.none(), false, "tester"));

        var current = service.getCurrentMarkdown(created.document().documentId());
        var revision = service.getRevisionMarkdown(created.document().documentId(), created.revision().revisionId());

        assertEquals(created.document().documentId(), current.documentId());
        assertEquals(created.revision().revisionId(), current.revisionId());
        assertEquals("sample.md", current.filename());
        assertEquals(created.revision().contentHash(), current.contentHash());
        assertEquals("# Hello", Files.readString(current.path()));
        assertEquals(current.path(), revision.path());
        assertEquals(current.contentLength(), revision.contentLength());
    }

    @Test
    void currentMarkdownUsesPromotedRevisionWhileNewerExtractionIsRunning() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "hello");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()),
                new CapturingPipeline());
        String documentId = "mdoc-current";
        String completedRevisionId = "mrev-completed";
        String runningRevisionId = "mrev-running";
        repository.saveDocument(new MarkdownDocument(
                documentId, 1L, completedRevisionId, CLOCK.instant(), CLOCK.instant().plusSeconds(1)));
        repository.saveRevision(new MarkdownRevision(
                completedRevisionId,
                documentId,
                1L,
                null,
                null,
                "TEXTRACT",
                "native",
                new ObjectMapper().writeValueAsString(MarkdownPipelineOptions.none()),
                "options-hash",
                "source-hash",
                "completed-hash",
                "# Completed",
                "sample.pdf",
                "pdf",
                null,
                null,
                MarkdownRevisionStatus.COMPLETED,
                null,
                null,
                CLOCK.instant(),
                CLOCK.instant(),
                CLOCK.instant().plusSeconds(1),
                CLOCK.instant().plusSeconds(1)));
        repository.saveRevision(new MarkdownRevision(
                runningRevisionId,
                documentId,
                1L,
                null,
                null,
                "TEXTRACT",
                "native",
                new ObjectMapper().writeValueAsString(MarkdownPipelineOptions.none()),
                "options-hash-2",
                "source-hash",
                null,
                null,
                "sample.pdf",
                "pdf",
                null,
                null,
                MarkdownRevisionStatus.RUNNING,
                null,
                null,
                CLOCK.instant().plusSeconds(2),
                CLOCK.instant().plusSeconds(2),
                null,
                CLOCK.instant().plusSeconds(2)));

        var current = service.getCurrentMarkdown(documentId);

        assertEquals(completedRevisionId, current.revisionId());
        assertEquals("# Completed", Files.readString(current.path()));
    }

    @Test
    void markdownContentRequiresCompletedRevisionWithText() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "pdf");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "", "textract-1", List.of(), List.of()),
                new CapturingPipeline());
        String documentId = "mdoc-running";
        String revisionId = "mrev-running";
        repository.saveDocument(new MarkdownDocument(documentId, 1L, revisionId, CLOCK.instant(), CLOCK.instant()));
        repository.saveRevision(new MarkdownRevision(
                revisionId,
                documentId,
                1L,
                null,
                null,
                "TEXTRACT",
                "native",
                new ObjectMapper().writeValueAsString(MarkdownPipelineOptions.none()),
                "options-hash",
                "source-hash",
                null,
                null,
                "sample.pdf",
                "pdf",
                null,
                null,
                MarkdownRevisionStatus.RUNNING,
                null,
                null,
                CLOCK.instant(),
                CLOCK.instant(),
                null,
                CLOCK.instant()));

        assertThrows(MarkdownContentUnavailableException.class, () -> service.getCurrentMarkdown(documentId));
    }

    private static MarkdownExtractPart extractPart(String revisionId, int pageFrom, int pageTo, String markdown) {
        return new MarkdownExtractPart(
                "mepart-" + pageFrom,
                revisionId,
                pageFrom,
                pageTo,
                "COMPLETED",
                "pymupdf4llm",
                markdown.length(),
                markdown,
                null,
                null,
                1L,
                "{}",
                CLOCK.instant(),
                CLOCK.instant(),
                CLOCK.instant());
    }

    @Test
    void applyIdeaBlockMergeCanResumeRagPipelineFromRagIndex() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        ApplyAndResumePipeline pipeline = new ApplyAndResumePipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()),
                pipeline);
        var created = service.create(new MarkdownExtractionRequest(
                1L, MarkdownPipelineOptions.none(), false, "tester"));

        MarkdownIdeaBlockMergeApplyResult result = service.applyIdeaBlockMerge(
                created.document().documentId(),
                created.revision().revisionId(),
                new MarkdownIdeaBlockMergeApplyOptions(
                        new MarkdownIdeaBlockMergePreviewOptions("sim-1", false, "test", "test-model", 5),
                        "sha256:test"),
                new MarkdownResumeOptions(
                        null,
                        null,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        "retrieval-ko-kure",
                        null,
                        null,
                        null,
                        true,
                        "llm",
                        true,
                        "kure",
                        "nlpai-lab/KURE-v1",
                        1024));

        assertEquals("merged-chunk-1", result.mergedChunkId());
        assertNotNull(result.pipelineResult());
        assertEquals("PIPELINE", result.pipelineResult().resumedPhase());
        assertEquals(MarkdownPipelineStage.RAG_INDEX, result.pipelineResult().resumedFrom());
        assertEquals(List.of(MarkdownPipelineStage.METADATA_ENRICHMENT, MarkdownPipelineStage.RAG_INDEX),
                pipeline.startedFrom);
        assertTrue(pipeline.options.runChunking());
        assertTrue(pipeline.options.runRagIndex());
        assertTrue(pipeline.options.runSkillExtraction());
        assertEquals("retrieval-ko-kure", pipeline.options.embeddingProfileId());
        assertTrue(pipeline.options.useLlmKeywordExtraction());
        assertEquals("llm", pipeline.options.skillExtractionMode());
    }

    @Test
    void autoApplyIdeaBlockMergeAppliesOnlyApplicablePreviewPlans() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        ApplyAndResumePipeline pipeline = new ApplyAndResumePipeline();
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Hello", "textract-1", List.of(), List.of()),
                pipeline);
        var created = service.create(new MarkdownExtractionRequest(
                1L, MarkdownPipelineOptions.none(), false, "tester"));

        MarkdownIdeaBlockMergeBatchApplyResult result = service.autoApplyIdeaBlockMerge(
                created.document().documentId(),
                created.revision().revisionId(),
                new MarkdownIdeaBlockMergePreviewOptions(null, true, "test", "test-model", 5),
                null);

        assertEquals(1, result.applied().size());
        assertEquals("sha256:test", result.applied().get(0).planFingerprint());
        assertEquals(0, result.failed().size());
        assertEquals("sha256:test", pipeline.appliedPlanFingerprint);
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
    void appliesNormalizationPortToPandocRevision() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        MarkdownNormalizationPort normalizer = request -> new MarkdownNormalizationPort.NormalizationResult(
                "# Normalized",
                request.locators(),
                List.of(new MarkdownResource("mres-1", request.revisionId(),
                        MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                        "normalized-document.json", null,
                        "{\"schemaVersion\":\"normalized-document-v1\"}")));
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> {
                    throw new AssertionError("native extractor must not run");
                }, MarkdownPipelinePort.noop(), normalizer);

        var created = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));
        service.onConversionResult(created.revision().documentConvertJobId(), "# Converted", 1L);
        MarkdownRevision completed = service.getRevisions(created.document().documentId()).get(0);

        assertEquals(MarkdownRevisionStatus.COMPLETED, completed.status());
        assertEquals("# Normalized", completed.markdownText());
        assertEquals(1, repository.findResources(completed.revisionId()).size());
        assertEquals(MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                repository.findResources(completed.revisionId()).get(0).resourceType());
    }

    @Test
    void normalizationReviewResourceDoesNotFailRevision() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "hello");
        MarkdownNormalizationPort normalizer = request -> new MarkdownNormalizationPort.NormalizationResult(
                request.markdown(),
                request.locators(),
                List.of(new MarkdownResource("mres-1", request.revisionId(),
                        MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                        "normalized-document.json", null,
                        "{\"schemaVersion\":\"normalized-document-v1\",\"normalizationStatus\":\"REVIEW_REQUIRED\"}")));
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop(), normalizer);

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        assertEquals(MarkdownRevisionStatus.COMPLETED, result.revision().status());
        assertEquals("# Native", result.revision().markdownText());
        assertEquals(1, repository.findResources(result.revision().revisionId()).size());
    }

    @Test
    void exposesNormalizedBlockProvenanceAsLocators() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.pdf", "application/pdf", "pdf");
        MarkdownNormalizationPort normalizer = request -> new MarkdownNormalizationPort.NormalizationResult(
                request.markdown(),
                request.locators(),
                List.of(new MarkdownResource("mres-1", request.revisionId(),
                        MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                        "normalized-document.json", null,
                        """
                                {
                                  "schemaVersion": "normalized-document-v1",
                                  "document": {
                                    "blocks": [
                                      {
                                        "id": "block-1",
                                        "type": "PARAGRAPH",
                                        "text": "본문",
                                        "sourceRef": "page[3]/block[1]",
                                        "order": 7,
                                        "metadata": {
                                          "bbox": [10.0, 20.0, 30.0, 40.0]
                                        }
                                      }
                                    ]
                                  }
                                }
                                """)));
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Native", "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop(), normalizer);

        var result = service.create(new MarkdownExtractionRequest(1L, false, false, false, false, "tester"));

        List<MarkdownLocator> locators = service.getLocators(result.document().documentId());
        assertEquals(1, locators.size());
        assertEquals("NORMALIZED_BLOCK", locators.get(0).locatorType());
        assertEquals(3, locators.get(0).locatorNo());
        assertEquals(3, locators.get(0).page());
        assertEquals(null, locators.get(0).slide());
        assertEquals(List.of(10.0, 20.0, 30.0, 40.0), locators.get(0).bbox());
        assertEquals("page[3]/block[1]", locators.get(0).sourceRef());
        assertTrue(locators.get(0).metadataJson().contains("\"bbox\""));
        assertEquals(locators, service.getProvenance(result.document().documentId()));
        assertEquals(1, repository.findLocators(result.revision().revisionId()).size());
        assertEquals("NORMALIZED_BLOCK",
                repository.findLocators(result.revision().revisionId()).get(0).locatorType());
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
                null, "google", "gemini-embedding-001", 768,
                true, "llm", true, "kure", "nlpai-lab/KURE-v1", 1024);

        var result = service.create(new MarkdownExtractionRequest(1L, options, false, "tester"));
        Map<String, Object> stored = new ObjectMapper().readValue(
                result.revision().optionsJson(), new TypeReference<>() {
                });

        assertTrue(pipeline.options.runChunking());
        assertTrue(pipeline.options.runRagIndex());
        assertTrue(pipeline.options.runSkillExtraction());
        assertEquals("fixed-size", stored.get("chunkingStrategy"));
        assertEquals(768, stored.get("embeddingDimension"));
        assertEquals(true, stored.get("useLlmKeywordExtraction"));
        assertEquals("llm", stored.get("skillExtractionMode"));
        assertEquals(true, stored.get("generateSkillEmbeddings"));
        assertEquals("kure", stored.get("skillEmbeddingProvider"));
        assertEquals("nlpai-lab/KURE-v1", stored.get("skillEmbeddingModel"));
        assertEquals(1024, stored.get("skillEmbeddingDimension"));
    }

    @Test
    void estimatesAttachmentFromSourceSizeWithLowConfidence() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "source");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Heading\n\nBody", "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop());
        MarkdownResumeOptions options = new MarkdownResumeOptions(
                null, true, true, false,
                "recursive", 800, 100, "CHARACTER",
                null, null, null,
                "gemini-768", "google-ai-gemini-embedding-001", "gemini-embedding-001", 768,
                false, null, false, null, null, null,
                null, null, null, null);

        var estimate = service.estimatePipelineByAttachment(1L, options);

        assertEquals("SOURCE_SIZE", estimate.estimateBasis());
        assertEquals("LOW", estimate.confidence());
        assertEquals("gemini-768", estimate.embedding().profileId());
        assertEquals(null, estimate.embedding().model());
        assertEquals(null, estimate.embedding().dimension());
    }

    @Test
    void estimatesCompletedRevisionFromActualContentWithHighConfidence() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        sources.add(1L, "sample.txt", "text/plain", "source");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        "# Heading\n\nBody", "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop());
        var extraction = service.create(new MarkdownExtractionRequest(
                1L, false, false, false, false, "tester"));

        var estimate = service.estimatePipeline(extraction.document().documentId(), null);

        assertEquals("REVISION_CONTENT", estimate.estimateBasis());
        assertEquals("HIGH", estimate.confidence());
        assertEquals(extraction.revision().markdownText().length(), estimate.markdownLength());
    }

    @Test
    void keepsAutomaticStrategyInLargeDocumentRecommendation() {
        InMemoryRepository repository = new InMemoryRepository();
        SourcePort sources = new SourcePort();
        String markdown = "body ".repeat(300_000);
        sources.add(1L, "sample.txt", "text/plain", "source");
        MarkdownDocumentService service = service(repository, sources,
                (source, revisionId) -> new MarkdownNativeExtractorPort.NativeExtraction(
                        markdown, "textract-1", List.of(), List.of()),
                MarkdownPipelinePort.noop());
        var extraction = service.create(new MarkdownExtractionRequest(
                1L, new MarkdownPipelineOptions(true, false, false), false, "tester"));

        var estimate = service.estimatePipeline(extraction.document().documentId(), null);

        assertEquals(null, estimate.recommended().chunkingStrategy());
        assertTrue(estimate.recommended().chunkMaxSize() > 1200);
    }

    private MarkdownDocumentService service(InMemoryRepository repository, SourcePort sources,
            MarkdownNativeExtractorPort extractor, MarkdownPipelinePort pipeline) {
        return service(repository, sources, extractor, pipeline, MarkdownTaskExecutor.direct());
    }

    private MarkdownPipelineOptions profiledOptions(boolean runChunking, boolean runRagIndex) {
        return new MarkdownPipelineOptions(
                runChunking,
                runRagIndex,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                true,
                "kor",
                "FORCE",
                true,
                "MATH_TEXTBOOK",
                "MATH_TEXTBOOK",
                "test-v1",
                null);
    }

    private MarkdownConversionPort conversion() {
        return new MarkdownConversionPort() {
            @Override
            public ConversionSubmission submit(String jobId, long sourceAttachmentId, String sourceFormat,
                    String requestedBy) {
                return new ConversionSubmission(jobId, "RUNNING", null, null);
            }

            @Override
            public void cancel(String jobId) {
            }
        };
    }

    private MarkdownDocumentService service(InMemoryRepository repository, SourcePort sources,
            MarkdownNativeExtractorPort extractor, MarkdownPipelinePort pipeline,
            MarkdownNormalizationPort normalizationPort) {
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
        return new MarkdownDocumentService(repository, sources, extractor, conversion, normalizationPort, pipeline,
                MarkdownTaskExecutor.direct(), MarkdownTransactionOperations.direct(),
                new ObjectMapper(), CLOCK, "pandoc-3", Set.of("docx", "html"), true);
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

    private MarkdownDocumentService service(InMemoryRepository repository, SourcePort sources,
            MarkdownNativeExtractorPort extractor, MarkdownConversionPort conversion, MarkdownPipelinePort pipeline,
            MarkdownTaskExecutor taskExecutor, Set<String> pandocFormats, boolean fallbackToNativeOnPandocFailure) {
        return new MarkdownDocumentService(repository, sources, extractor, conversion, pipeline,
                taskExecutor, MarkdownTransactionOperations.direct(),
                new ObjectMapper(), CLOCK, "pandoc-3", pandocFormats, fallbackToNativeOnPandocFailure);
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
            MarkdownPipelinePlan plan = MarkdownPipelinePlan.of(options);
            if (plan.shouldRunFrom(fromStage, MarkdownPipelineStage.METADATA_ENRICHMENT)) {
                completed.accept(MarkdownPipelineStage.METADATA_ENRICHMENT);
            }
            if (plan.shouldRunFrom(fromStage, MarkdownPipelineStage.CHUNKING)) {
                completed.accept(MarkdownPipelineStage.CHUNKING);
            }
            if (plan.shouldRunFrom(fromStage, MarkdownPipelineStage.RAG_INDEX)) {
                if (failRag) {
                    throw new IllegalStateException("rag failed");
                }
                completed.accept(MarkdownPipelineStage.RAG_INDEX);
            }
        }
    }

    private static final class ApplyAndResumePipeline implements MarkdownPipelinePort {
        private final List<MarkdownPipelineStage> startedFrom = new ArrayList<>();
        private MarkdownPipelineOptions options;
        private String appliedPlanFingerprint;

        @Override
        public void process(MarkdownRevision revision, boolean runChunking, boolean runRagIndex,
                boolean runSkillExtraction) {
        }

        @Override
        public void process(MarkdownRevision revision, MarkdownPipelineOptions options,
                MarkdownPipelineStage fromStage, java.util.function.Consumer<MarkdownPipelineStage> completed) {
            this.options = options;
            startedFrom.add(fromStage);
            completed.accept(MarkdownPipelineStage.RAG_INDEX);
            if (options.runSkillExtraction()) {
                completed.accept(MarkdownPipelineStage.SKILL_EXTRACTION);
            }
        }

        @Override
        public MarkdownIdeaBlockMergeApplyResult ideaBlockMergeApply(
                MarkdownRevision revision,
                MarkdownIdeaBlockMergeApplyOptions options) {
            this.appliedPlanFingerprint = options.planFingerprint();
            return new MarkdownIdeaBlockMergeApplyResult(
                    revision.documentId(),
                    revision.revisionId(),
                    "merge-plan:lexical:sim-1",
                    options.planFingerprint(),
                    "merged-chunk-1",
                    List.of("chunk-1", "chunk-2"),
                    3,
                    2,
                    null);
        }

        @Override
        public MarkdownIdeaBlockMergePreview ideaBlockMergePreview(
                MarkdownRevision revision,
                MarkdownIdeaBlockMergePreviewOptions options) {
            return new MarkdownIdeaBlockMergePreview(
                    revision.documentId(),
                    revision.revisionId(),
                    true,
                    options.llmProvider(),
                    options.llmModel(),
                    List.of(new MarkdownIdeaBlockMergePreview.ClusterPreview(
                            "sim-1",
                            "embedding",
                            List.of("chunk-1", "chunk-2"),
                            "PREVIEW",
                            "LLM_PREVIEW",
                            "휴가 규정은 무엇인가?",
                            "휴가 규정은 원문 근거에 따라 적용된다.",
                            List.of("휴가"),
                            List.of("규정"),
                            List.of(Map.of("text", "휴가 규정은 원문 근거에 따라 적용된다.")),
                            List.of(Map.of("start", 1, "end", 2)),
                            "LLM_MERGE_PREVIEW",
                            "## Critical Question\n휴가 규정은 무엇인가?\n## Trusted Answer\n휴가 규정은 원문 근거에 따라 적용된다.",
                            "merge-plan:embedding:sim-1",
                            "sha256:test",
                            true,
                            List.of(),
                            List.of("chunk-1", "chunk-2"))));
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
        private final Map<String, List<studio.one.platform.markdown.domain.MarkdownExtractPart>> extractParts =
                new HashMap<>();
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
        public void replaceExtractParts(String revisionId,
                List<studio.one.platform.markdown.domain.MarkdownExtractPart> values) {
            extractParts.put(revisionId, List.copyOf(values));
        }

        @Override
        public void deleteExtractParts(String revisionId) {
            extractParts.remove(revisionId);
        }

        @Override
        public void saveExtractPart(studio.one.platform.markdown.domain.MarkdownExtractPart part) {
            List<studio.one.platform.markdown.domain.MarkdownExtractPart> values =
                    new ArrayList<>(extractParts.getOrDefault(part.revisionId(), List.of()));
            values.add(part);
            extractParts.put(part.revisionId(), List.copyOf(values));
        }

        @Override
        public List<MarkdownLocator> findLocators(String revisionId) {
            return locators.getOrDefault(revisionId, List.of());
        }

        @Override
        public List<MarkdownResource> findResources(String revisionId) {
            return resources.getOrDefault(revisionId, List.of());
        }

        @Override
        public List<studio.one.platform.markdown.domain.MarkdownExtractPart> findExtractParts(String revisionId) {
            return extractParts.getOrDefault(revisionId, List.of());
        }
    }
}
