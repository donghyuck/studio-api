package studio.one.platform.markdown.application;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

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

@Transactional
public class MarkdownDocumentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MarkdownRepository repository;
    private final MarkdownSourcePort sourcePort;
    private final MarkdownNativeExtractorPort nativeExtractor;
    private final MarkdownConversionPort conversionPort;
    private final MarkdownNormalizationPort normalizationPort;
    private final MarkdownPipelinePort pipelinePort;
    private final MarkdownTaskExecutor taskExecutor;
    private final MarkdownTransactionOperations transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String pandocVersion;
    private final Set<String> pandocSourceFormats;
    private final boolean fallbackToNativeOnPandocFailure;
    private final Path markdownResultDirectory;
    private final MarkdownPagePreviewPort pagePreviewPort;
    private final String webBasePath;
    private final Set<String> activeTasks = ConcurrentHashMap.newKeySet();
    private static final int ESTIMATE_TARGET_CHUNKS = 1000;
    private static final int ESTIMATE_DEFAULT_EMBEDDING_BATCH_SIZE = 4;
    private static final int STALE_PIPELINE_RECOVERY_MINUTES = 30;
    private static final Set<String> DEFAULT_PANDOC_SOURCE_FORMATS = Set.of("docx", "html");
    private static final Path DEFAULT_MARKDOWN_RESULT_DIRECTORY =
            Path.of(System.getProperty("java.io.tmpdir"), "studio-markdown-results");
    private static final String DEFAULT_WEB_BASE_PATH = "/api/markdown-documents";
    private static final Pattern LOGICAL_IMAGE_REFERENCE =
            Pattern.compile("\\]\\(page\\[(\\d+)]/image\\[(\\d+)](?:\\.[^)]+)?\\)");

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownPipelinePort pipelinePort, ObjectMapper objectMapper, Clock clock, String pandocVersion) {
        this(repository, sourcePort, nativeExtractor, conversionPort, pipelinePort,
                MarkdownTaskExecutor.direct(), MarkdownTransactionOperations.direct(),
                objectMapper, clock, pandocVersion);
    }

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, Clock clock, String pandocVersion) {
        this(repository, sourcePort, nativeExtractor, conversionPort, pipelinePort, taskExecutor, transactions,
                objectMapper, clock, pandocVersion, DEFAULT_PANDOC_SOURCE_FORMATS, true);
    }

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, Clock clock, String pandocVersion,
            Set<String> pandocSourceFormats, boolean fallbackToNativeOnPandocFailure) {
        this(repository, sourcePort, nativeExtractor, conversionPort, MarkdownNormalizationPort.noop(),
                pipelinePort, taskExecutor, transactions, objectMapper, clock, pandocVersion,
                pandocSourceFormats, fallbackToNativeOnPandocFailure);
    }

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownNormalizationPort normalizationPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, Clock clock, String pandocVersion,
            Set<String> pandocSourceFormats, boolean fallbackToNativeOnPandocFailure) {
        this(repository, sourcePort, nativeExtractor, conversionPort, normalizationPort, pipelinePort, taskExecutor,
                transactions, objectMapper, clock, pandocVersion, pandocSourceFormats,
                fallbackToNativeOnPandocFailure, DEFAULT_MARKDOWN_RESULT_DIRECTORY,
                MarkdownPagePreviewPort.unsupported(), DEFAULT_WEB_BASE_PATH);
    }

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownNormalizationPort normalizationPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, Clock clock, String pandocVersion,
            Set<String> pandocSourceFormats, boolean fallbackToNativeOnPandocFailure,
            Path markdownResultDirectory) {
        this(repository, sourcePort, nativeExtractor, conversionPort, normalizationPort, pipelinePort, taskExecutor,
                transactions, objectMapper, clock, pandocVersion, pandocSourceFormats, fallbackToNativeOnPandocFailure,
                markdownResultDirectory, MarkdownPagePreviewPort.unsupported(), DEFAULT_WEB_BASE_PATH);
    }

    public MarkdownDocumentService(MarkdownRepository repository, MarkdownSourcePort sourcePort,
            MarkdownNativeExtractorPort nativeExtractor, MarkdownConversionPort conversionPort,
            MarkdownNormalizationPort normalizationPort,
            MarkdownPipelinePort pipelinePort, MarkdownTaskExecutor taskExecutor,
            MarkdownTransactionOperations transactions,
            ObjectMapper objectMapper, Clock clock, String pandocVersion,
            Set<String> pandocSourceFormats, boolean fallbackToNativeOnPandocFailure,
            Path markdownResultDirectory, MarkdownPagePreviewPort pagePreviewPort, String webBasePath) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort");
        this.nativeExtractor = Objects.requireNonNull(nativeExtractor, "nativeExtractor");
        this.conversionPort = Objects.requireNonNull(conversionPort, "conversionPort");
        this.normalizationPort = normalizationPort == null ? MarkdownNormalizationPort.noop() : normalizationPort;
        this.pipelinePort = pipelinePort == null ? MarkdownPipelinePort.noop() : pipelinePort;
        this.taskExecutor = taskExecutor == null ? MarkdownTaskExecutor.direct() : taskExecutor;
        this.transactions = transactions == null ? MarkdownTransactionOperations.direct() : transactions;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pandocVersion = normalize(pandocVersion, "pandoc");
        this.pandocSourceFormats = normalizeFormats(pandocSourceFormats);
        this.fallbackToNativeOnPandocFailure = fallbackToNativeOnPandocFailure;
        this.markdownResultDirectory = markdownResultDirectory == null
                ? DEFAULT_MARKDOWN_RESULT_DIRECTORY : markdownResultDirectory;
        this.pagePreviewPort = pagePreviewPort == null ? MarkdownPagePreviewPort.unsupported() : pagePreviewPort;
        this.webBasePath = normalizeWebBasePath(webBasePath);
        recoverStalePipelineExecutions();
    }

    public MarkdownExtractionResult create(MarkdownExtractionRequest request) {
        MarkdownSourcePort.MarkdownSource source = sourcePort.load(request.attachmentId());
        String sourceFormat = sourceFormat(source.fileName(), source.contentType());
        boolean pandoc = shouldUsePandoc(sourceFormat);
        String extractorType = pandoc ? "PANDOC" : "TEXTRACT";
        String extractorVersion = pandoc ? pandocVersion : "native";
        String sourceHash = hash(source.content());
        String optionsJson = writeOptions(request);
        String optionsHash = hash(optionsJson.getBytes(StandardCharsets.UTF_8));

        if (!request.force()) {
            var reusable = repository.findReusableRevision(source.attachmentId(), sourceHash,
                    extractorType, extractorVersion, optionsHash);
            if (reusable.isPresent()) {
                MarkdownRevision revision = reusable.get();
                if (hasText(revision.markdownText())) {
                    return new MarkdownExtractionResult(requireDocument(revision.documentId()), revision, true);
                }
            }
        }

        Instant now = clock.instant();
        MarkdownDocument document = repository.findDocumentBySourceAttachmentId(source.attachmentId())
                .orElseGet(() -> repository.saveDocument(new MarkdownDocument(
                        "mdoc-" + UUID.randomUUID(), source.attachmentId(), null, now, now)));
        String revisionId = "mrev-" + UUID.randomUUID();
        String conversionJobId = pandoc ? "conv-md-" + UUID.randomUUID() : null;
        MarkdownRevision revision = new MarkdownRevision(
                revisionId, document.documentId(), source.attachmentId(), null, conversionJobId,
                extractorType, extractorVersion, optionsJson, optionsHash, sourceHash, null, null,
                source.fileName(), sourceFormat, source.objectType(), source.objectId(),
                MarkdownRevisionStatus.PENDING, null, null, now, null, null, now);
        revision = repository.saveRevision(revision);

        if (pandoc) {
            revision = markRunning(revision);
            MarkdownConversionPort.ConversionSubmission submission;
            try {
                submission = conversionPort.submit(conversionJobId,
                        source.attachmentId(), sourceFormat, request.requestedBy());
            } catch (RuntimeException ex) {
                if (!fallbackToNativeOnPandocFailure) {
                    throw ex;
                }
                return fallbackToNative(document, revision, "PANDOC_SUBMIT_FAILED", ex.getMessage());
            }
            MarkdownRevision current = repository.findRevision(revision.revisionId()).orElse(revision);
            if (current.status().terminal() || !"PANDOC".equalsIgnoreCase(current.extractorType())) {
                return new MarkdownExtractionResult(requireDocument(document.documentId()), current, false);
            }
            revision = withConversionJob(current, submission.jobId());
            if ("FAILED".equalsIgnoreCase(submission.status())) {
                if (fallbackToNativeOnPandocFailure) {
                    return fallbackToNative(document, revision, submission.errorCode(), submission.errorMessage());
                }
                revision = fail(revision, submission.errorCode(), submission.errorMessage());
            }
            return new MarkdownExtractionResult(document, revision, false);
        }

        revision = markRunning(revision);
        String scheduledRevisionId = revision.revisionId();
        scheduleNative(scheduledRevisionId);
        MarkdownRevision current = repository.findRevision(scheduledRevisionId).orElse(revision);
        return new MarkdownExtractionResult(requireDocument(document.documentId()), current, false);
    }

    public void onConversionCompleted(String jobId, long resultAttachmentId) {
        onConversionCompleted(jobId, resultAttachmentId, null);
    }

    public void onConversionResult(String jobId, String markdown, Long sourceAttachmentId) {
        MarkdownRevision revision = findConversionRevision(jobId, sourceAttachmentId);
        if (revision == null || revision.status().terminal()) {
            return;
        }
        if (revision.documentConvertJobId() == null) {
            revision = withConversionJob(revision, jobId);
        }
        try {
            MarkdownRevision completed = complete(revision, null, markdown,
                    revision.extractorVersion(), headingLocators(revision.revisionId(), markdown), List.of());
            prepareAndSchedulePipeline(completed);
        } catch (RuntimeException ex) {
            fail(revision, extractionErrorCode(ex, "REVISION_STORE_FAILED"), ex.getMessage());
        }
    }

    public void onConversionCompleted(String jobId, long resultAttachmentId, Long sourceAttachmentId) {
        MarkdownRevision revision = findConversionRevision(jobId, sourceAttachmentId);
        if (revision == null || revision.status().terminal()) {
            return;
        }
        if (revision.documentConvertJobId() == null) {
            revision = withConversionJob(revision, jobId);
        }
        try {
            MarkdownSourcePort.MarkdownSource result = sourcePort.load(resultAttachmentId);
            String markdown = new String(result.content(), StandardCharsets.UTF_8);
            MarkdownRevision completed = complete(revision, resultAttachmentId, markdown,
                    revision.extractorVersion(), headingLocators(revision.revisionId(), markdown), List.of());
            prepareAndSchedulePipeline(completed);
        } catch (RuntimeException ex) {
            fail(revision, extractionErrorCode(ex, "REVISION_STORE_FAILED"), ex.getMessage());
        }
    }

    public void onConversionFailed(String jobId, String errorCode, String errorMessage) {
        onConversionFailed(jobId, null, errorCode, errorMessage);
    }

    public void onConversionFailed(String jobId, Long sourceAttachmentId, String errorCode, String errorMessage) {
        MarkdownRevision revision = findConversionRevision(jobId, sourceAttachmentId);
        if (revision != null && !revision.status().terminal()) {
            if (revision.documentConvertJobId() == null) {
                revision = withConversionJob(revision, jobId);
            }
            if (fallbackToNativeOnPandocFailure) {
                fallbackToNative(requireDocument(revision.documentId()), revision, errorCode, errorMessage);
                return;
            }
            fail(revision, errorCode, errorMessage);
        }
    }

    public void onConversionCanceled(String jobId, Long sourceAttachmentId) {
        MarkdownRevision revision = findConversionRevision(jobId, sourceAttachmentId);
        if (revision == null || revision.status().terminal()) {
            return;
        }
        Instant now = clock.instant();
        repository.saveRevision(copy(revision, MarkdownRevisionStatus.CANCELED, revision.markdownText(),
                revision.contentHash(), revision.resultAttachmentId(), jobId, revision.extractorVersion(),
                null, null, revision.startedAt(), now, now));
    }

    public MarkdownDocument getDocument(String documentId) {
        return requireDocument(documentId);
    }

    public MarkdownDocument getDocumentBySourceAttachmentId(long sourceAttachmentId) {
        return repository.findDocumentBySourceAttachmentId(sourceAttachmentId)
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown document not found for attachment: " + sourceAttachmentId));
    }

    public List<MarkdownRevision> getRevisions(String documentId) {
        requireDocument(documentId);
        return repository.findRevisions(documentId);
    }

    public MarkdownContent getCurrentMarkdown(String documentId) {
        MarkdownDocument document = requireDocument(documentId);
        MarkdownRevision revision = hasText(document.currentRevisionId())
                ? repository.findRevision(document.currentRevisionId())
                        .filter(value -> documentId.equals(value.documentId()))
                        .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                                "Current Markdown revision not found: " + document.currentRevisionId()))
                : latestRevision(documentId);
        return markdownContent(revision);
    }

    public MarkdownContent getRevisionMarkdown(String documentId, String revisionId) {
        requireDocument(documentId);
        MarkdownRevision revision = repository.findRevision(revisionId)
                .filter(value -> documentId.equals(value.documentId()))
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown revision not found: " + revisionId));
        return markdownContent(revision);
    }

    public MarkdownPagePreview getPagePreview(String documentId, int page, MarkdownPagePreviewBounds bounds) {
        MarkdownDocument document = requireDocument(documentId);
        if (page < 1) {
            throw new IllegalArgumentException("Page must be at least 1");
        }
        MarkdownRevision revision = hasText(document.currentRevisionId())
                ? repository.findRevision(document.currentRevisionId())
                        .filter(value -> documentId.equals(value.documentId()))
                        .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                                "Current Markdown revision not found: " + document.currentRevisionId()))
                : latestRevision(documentId);
        MarkdownSourcePort.MarkdownSource source = sourcePort.load(revision.sourceAttachmentId());
        if (!isPdf(source)) {
            throw new MarkdownPagePreviewUnavailableException("Page preview is available only for PDF sources");
        }
        String sourceHash = hasText(revision.sourceContentHash()) ? revision.sourceContentHash() : hash(source.content());
        Path path = previewPath(sourceHash, page, bounds);
        if (!Files.exists(path)) {
            byte[] rendered = pagePreviewPort.renderPng(source, page, bounds);
            if (rendered == null || rendered.length == 0) {
                throw new MarkdownPagePreviewUnavailableException("PDF page preview renderer returned no image");
            }
            writePreviewFile(path, rendered);
        }
        try {
            return new MarkdownPagePreview(documentId, revision.revisionId(), page, bounds, path, Files.size(path));
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException("Failed to read PDF page preview: " + path, ex);
        }
    }

    public MarkdownPipelineExecution getPipelineExecution(String documentId) {
        MarkdownRevision revision = latestRevision(documentId);
        return repository.findPipelineExecution(revision.revisionId())
                .orElseGet(() -> legacyPipelineExecution(revision));
    }

    public MarkdownPipelineProgress getPipelineProgress(String documentId) {
        MarkdownRevision revision = latestRevision(documentId);
        MarkdownPipelineExecution execution = repository.findPipelineExecution(revision.revisionId())
                .orElseGet(() -> legacyPipelineExecution(revision));
        return new MarkdownPipelineProgress(execution, pipelinePort.latestChunkingProgress(revision),
                pipelinePort.latestRagProgress(revision));
    }

    public MarkdownIdeaBlockSummary getIdeaBlockSummary(String documentId, String revisionId) {
        requireDocument(documentId);
        MarkdownRevision revision = repository.findRevision(revisionId)
                .filter(value -> documentId.equals(value.documentId()))
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown revision not found: " + revisionId));
        MarkdownIdeaBlockSummary summary = pipelinePort.ideaBlockSummary(revision);
        return summary == null ? new MarkdownIdeaBlockSummary(documentId, revisionId, 0.0d, 0, 0, 0,
                "NOT_AVAILABLE", 0, 0, 0, 0, null, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                : summary;
    }

    public MarkdownIdeaBlockMergePreview getIdeaBlockMergePreview(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergePreviewOptions options) {
        requireDocument(documentId);
        MarkdownRevision revision = repository.findRevision(revisionId)
                .filter(value -> documentId.equals(value.documentId()))
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown revision not found: " + revisionId));
        MarkdownIdeaBlockMergePreview preview = pipelinePort.ideaBlockMergePreview(
                revision,
                options == null ? MarkdownIdeaBlockMergePreviewOptions.defaults() : options);
        return preview == null ? new MarkdownIdeaBlockMergePreview(
                documentId, revisionId, false, null, null, List.of()) : preview;
    }

    public MarkdownIdeaBlockMergeApplyResult applyIdeaBlockMerge(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergeApplyOptions options) {
        requireDocument(documentId);
        MarkdownRevision revision = repository.findRevision(revisionId)
                .filter(value -> documentId.equals(value.documentId()))
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown revision not found: " + revisionId));
        MarkdownIdeaBlockMergeApplyResult result = pipelinePort.ideaBlockMergeApply(revision, options);
        if (result == null) {
            throw new IllegalStateException("IdeaBlock merge apply is not configured");
        }
        return result;
    }

    public MarkdownIdeaBlockMergeApplyResult applyIdeaBlockMerge(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergeApplyOptions options,
            MarkdownResumeOptions downstreamOptions) {
        MarkdownIdeaBlockMergeApplyResult result = applyIdeaBlockMerge(documentId, revisionId, options);
        MarkdownResumeResult pipelineResult = null;
        if (downstreamOptions != null && Boolean.TRUE.equals(downstreamOptions.runRagIndex())) {
            MarkdownResumeOptions resumeOptions = new MarkdownResumeOptions(
                    MarkdownPipelineStage.RAG_INDEX,
                    false,
                    true,
                    downstreamOptions.runSkillExtraction(),
                    downstreamOptions.chunkingStrategy(),
                    downstreamOptions.chunkMaxSize(),
                    downstreamOptions.chunkOverlap(),
                    downstreamOptions.chunkUnit(),
                    downstreamOptions.blockifyLlmProvider(),
                    downstreamOptions.blockifyLlmModel(),
                    downstreamOptions.blockifyPiiMaskingEnabled(),
                    downstreamOptions.embeddingProfileId(),
                    downstreamOptions.embeddingProvider(),
                    downstreamOptions.embeddingModel(),
                    downstreamOptions.embeddingDimension(),
                    downstreamOptions.useLlmKeywordExtraction(),
                    downstreamOptions.skillExtractionMode(),
                    downstreamOptions.generateSkillEmbeddings(),
                    downstreamOptions.skillEmbeddingProvider(),
                    downstreamOptions.skillEmbeddingModel(),
                    downstreamOptions.skillEmbeddingDimension());
            pipelineResult = resumeWithOptions(documentId, resumeOptions);
        }
        return new MarkdownIdeaBlockMergeApplyResult(
                result.documentId(),
                result.revisionId(),
                result.planId(),
                result.planFingerprint(),
                result.mergedChunkId(),
                result.mergedFromChunkIds(),
                result.beforeChunkCount(),
                result.afterChunkCount(),
                pipelineResult);
    }

    public MarkdownIdeaBlockMergeBatchApplyResult applyIdeaBlockMergeBatch(
            String documentId,
            String revisionId,
            List<MarkdownIdeaBlockMergeApplyOptions> items,
            MarkdownResumeOptions downstreamOptions) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Merge apply batch items are required");
        }
        List<MarkdownIdeaBlockMergeBatchApplyResult.Applied> applied = new ArrayList<>();
        List<MarkdownIdeaBlockMergeBatchApplyResult.Failed> failed = new ArrayList<>();
        for (MarkdownIdeaBlockMergeApplyOptions item : items) {
            try {
                MarkdownIdeaBlockMergeApplyResult result = applyIdeaBlockMerge(documentId, revisionId, item);
                applied.add(new MarkdownIdeaBlockMergeBatchApplyResult.Applied(
                        result.planId(),
                        result.planFingerprint(),
                        result.mergedChunkId(),
                        result.mergedFromChunkIds(),
                        result.beforeChunkCount(),
                        result.afterChunkCount()));
            } catch (RuntimeException ex) {
                failed.add(new MarkdownIdeaBlockMergeBatchApplyResult.Failed(
                        item == null ? null : item.planFingerprint(),
                        ex.getMessage()));
            }
        }
        MarkdownResumeResult pipelineResult = null;
        if (!applied.isEmpty() && downstreamOptions != null && Boolean.TRUE.equals(downstreamOptions.runRagIndex())) {
            MarkdownResumeOptions resumeOptions = new MarkdownResumeOptions(
                    MarkdownPipelineStage.RAG_INDEX,
                    false,
                    true,
                    downstreamOptions.runSkillExtraction(),
                    downstreamOptions.chunkingStrategy(),
                    downstreamOptions.chunkMaxSize(),
                    downstreamOptions.chunkOverlap(),
                    downstreamOptions.chunkUnit(),
                    downstreamOptions.blockifyLlmProvider(),
                    downstreamOptions.blockifyLlmModel(),
                    downstreamOptions.blockifyPiiMaskingEnabled(),
                    downstreamOptions.embeddingProfileId(),
                    downstreamOptions.embeddingProvider(),
                    downstreamOptions.embeddingModel(),
                    downstreamOptions.embeddingDimension(),
                    downstreamOptions.useLlmKeywordExtraction(),
                    downstreamOptions.skillExtractionMode(),
                    downstreamOptions.generateSkillEmbeddings(),
                    downstreamOptions.skillEmbeddingProvider(),
                    downstreamOptions.skillEmbeddingModel(),
                    downstreamOptions.skillEmbeddingDimension());
            pipelineResult = resumeWithOptions(documentId, resumeOptions);
        }
        return new MarkdownIdeaBlockMergeBatchApplyResult(
                documentId,
                revisionId,
                List.copyOf(applied),
                List.copyOf(failed),
                pipelineResult);
    }

    public MarkdownIdeaBlockMergeBatchApplyResult autoApplyIdeaBlockMerge(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergePreviewOptions previewOptions,
            MarkdownResumeOptions downstreamOptions) {
        MarkdownIdeaBlockMergePreview preview = getIdeaBlockMergePreview(documentId, revisionId,
                previewOptions == null ? MarkdownIdeaBlockMergePreviewOptions.defaults() : previewOptions);
        List<MarkdownIdeaBlockMergeApplyOptions> applicableItems = preview.clusters().stream()
                .filter(MarkdownIdeaBlockMergePreview.ClusterPreview::applicable)
                .map(cluster -> new MarkdownIdeaBlockMergeApplyOptions(
                        new MarkdownIdeaBlockMergePreviewOptions(
                                cluster.clusterId(),
                                "embedding".equals(cluster.clusterType()),
                                preview.llmProvider(),
                                preview.llmModel(),
                                1),
                        cluster.planFingerprint()))
                .toList();
        if (applicableItems.isEmpty()) {
            return new MarkdownIdeaBlockMergeBatchApplyResult(
                    documentId,
                    revisionId,
                    List.of(),
                    preview.clusters().stream()
                            .map(cluster -> new MarkdownIdeaBlockMergeBatchApplyResult.Failed(
                                    cluster.planFingerprint(),
                                    "IdeaBlock merge preview is not applicable: "
                                            + String.join(",", cluster.validationWarnings())))
                            .toList(),
                    null);
        }
        return applyIdeaBlockMergeBatch(documentId, revisionId, applicableItems, downstreamOptions);
    }

    public MarkdownIdeaBlockMergeUndoResult undoIdeaBlockMerge(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergeUndoOptions options) {
        requireDocument(documentId);
        MarkdownRevision revision = repository.findRevision(revisionId)
                .filter(value -> documentId.equals(value.documentId()))
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown revision not found: " + revisionId));
        MarkdownIdeaBlockMergeUndoResult result = pipelinePort.ideaBlockMergeUndo(revision, options);
        if (result == null) {
            throw new IllegalStateException("IdeaBlock merge undo is not configured");
        }
        return result;
    }

    public MarkdownIdeaBlockMergeUndoResult undoIdeaBlockMerge(
            String documentId,
            String revisionId,
            MarkdownIdeaBlockMergeUndoOptions options,
            MarkdownResumeOptions downstreamOptions) {
        MarkdownIdeaBlockMergeUndoResult result = undoIdeaBlockMerge(documentId, revisionId, options);
        MarkdownResumeResult pipelineResult = null;
        if (downstreamOptions != null && Boolean.TRUE.equals(downstreamOptions.runRagIndex())) {
            MarkdownResumeOptions resumeOptions = new MarkdownResumeOptions(
                    MarkdownPipelineStage.RAG_INDEX,
                    false,
                    true,
                    downstreamOptions.runSkillExtraction(),
                    downstreamOptions.chunkingStrategy(),
                    downstreamOptions.chunkMaxSize(),
                    downstreamOptions.chunkOverlap(),
                    downstreamOptions.chunkUnit(),
                    downstreamOptions.blockifyLlmProvider(),
                    downstreamOptions.blockifyLlmModel(),
                    downstreamOptions.blockifyPiiMaskingEnabled(),
                    downstreamOptions.embeddingProfileId(),
                    downstreamOptions.embeddingProvider(),
                    downstreamOptions.embeddingModel(),
                    downstreamOptions.embeddingDimension(),
                    downstreamOptions.useLlmKeywordExtraction(),
                    downstreamOptions.skillExtractionMode(),
                    downstreamOptions.generateSkillEmbeddings(),
                    downstreamOptions.skillEmbeddingProvider(),
                    downstreamOptions.skillEmbeddingModel(),
                    downstreamOptions.skillEmbeddingDimension());
            pipelineResult = resumeWithOptions(documentId, resumeOptions);
        }
        return new MarkdownIdeaBlockMergeUndoResult(
                result.documentId(),
                result.revisionId(),
                result.mergedChunkId(),
                result.planFingerprint(),
                result.restoredChunkIds(),
                result.beforeChunkCount(),
                result.afterChunkCount(),
                pipelineResult);
    }

    public MarkdownPipelineEstimate estimatePipeline(String documentId, MarkdownResumeOptions request) {
        MarkdownRevision revision = latestRevision(documentId);
        if (revision.status() != MarkdownRevisionStatus.COMPLETED || !hasText(revision.markdownText())) {
            throw new MarkdownPipelineEstimateUnavailableException(
                    "Completed Markdown revision with text is required for pipeline estimate");
        }
        MarkdownPipelineOptions previous = readOptions(revision.optionsJson());
        MarkdownPipelineOptions options = request == null ? previous : merge(previous, request);
        int pageCount = pageCount(revision.revisionId());
        int markdownLength = revision.markdownText().length();
        int chunkCount = pipelinePort.estimateChunkCount(revision, options);
        int embeddingBatchSize = ESTIMATE_DEFAULT_EMBEDDING_BATCH_SIZE;
        int embeddingRequests = embeddingRequests(chunkCount, embeddingBatchSize);
        RecommendedEstimate recommended = recommendedEstimate(revision, options, chunkCount, embeddingBatchSize);
        List<MarkdownPipelineEstimate.Warning> warnings = estimateWarnings(markdownLength, pageCount, chunkCount);
        return new MarkdownPipelineEstimate(
                revision.documentId(),
                revision.revisionId(),
                revision.sourceFileName(),
                revision.sourceFormat(),
                markdownLength,
                pageCount,
                markdownLength,
                chunkCount,
                embeddingRequests,
                embeddingBatchSize,
                "REVISION_CONTENT",
                "HIGH",
                riskLevel(chunkCount),
                recommended.toResponse(),
                embeddingSelection(options),
                warnings);
    }

    public MarkdownPipelineEstimate estimatePipelineByAttachment(long attachmentId, MarkdownResumeOptions request) {
        var document = repository.findDocumentBySourceAttachmentId(attachmentId);
        if (document.isPresent() && document.get().currentRevisionId() != null) {
            MarkdownRevision revision = repository.findRevision(document.get().currentRevisionId()).orElse(null);
            if (revision != null && revision.status() == MarkdownRevisionStatus.COMPLETED
                    && hasText(revision.markdownText())) {
                return estimatePipeline(document.get().documentId(), request);
            }
        }
        MarkdownSourcePort.MarkdownSourceDescriptor source = sourcePort.describe(attachmentId);
        MarkdownPipelineOptions options = request == null ? MarkdownPipelineOptions.none()
                : merge(new MarkdownPipelineOptions(true, true, false), request);
        int markdownLengthEstimate = Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0L, source.size())));
        int chunkCount = heuristicChunkCount(markdownLengthEstimate, options);
        int embeddingBatchSize = ESTIMATE_DEFAULT_EMBEDDING_BATCH_SIZE;
        int embeddingRequests = embeddingRequests(chunkCount, embeddingBatchSize);
        RecommendedEstimate recommended = heuristicRecommendedEstimate(options, markdownLengthEstimate, chunkCount,
                embeddingBatchSize);
        List<MarkdownPipelineEstimate.Warning> warnings = estimateWarnings(markdownLengthEstimate, 0, chunkCount);
        return new MarkdownPipelineEstimate(
                document.map(MarkdownDocument::documentId).orElse(null),
                null,
                source.fileName(),
                sourceFormat(source.fileName(), source.contentType()),
                source.size(),
                0,
                markdownLengthEstimate,
                chunkCount,
                embeddingRequests,
                embeddingBatchSize,
                "SOURCE_SIZE",
                "LOW",
                riskLevel(chunkCount),
                recommended.toResponse(),
                embeddingSelection(options),
                warnings);
    }

    public MarkdownResumeResult resume(String documentId, MarkdownPipelineStage requestedStage) {
        return resumeWithOptions(documentId, MarkdownResumeOptions.fromStage(requestedStage));
    }

    public MarkdownResumeResult resumeWithOptions(String documentId, MarkdownResumeOptions request) {
        MarkdownDocument document = requireDocument(documentId);
        MarkdownRevision revision = latestRevision(documentId);
        MarkdownPipelineStage requestedStage = request == null ? null : request.fromStage();

        if (revision.status() != MarkdownRevisionStatus.COMPLETED) {
            if (revision.status().terminal() || "PANDOC".equalsIgnoreCase(revision.extractorType())) {
                MarkdownPipelineOptions previous = readOptions(revision.optionsJson());
                MarkdownPipelineOptions options = request == null ? previous : merge(previous, request);
                MarkdownExtractionResult restarted = create(new MarkdownExtractionRequest(
                        revision.sourceAttachmentId(), options, true, "resume"));
                return new MarkdownResumeResult(restarted.document(), restarted.revision(), null,
                        "EXTRACTION", null);
            }
            MarkdownRevision recovered = recoverCompletedNativeExtractParts(revision);
            if (recovered != null) {
                MarkdownPipelineExecution execution = repository.findPipelineExecution(recovered.revisionId())
                        .orElse(null);
                String phase = execution != null
                        && execution.status() == MarkdownPipelineExecutionStatus.COMPLETED ? "COMPLETED" : "PIPELINE";
                return new MarkdownResumeResult(requireDocument(document.documentId()), recovered, execution,
                        phase, execution == null ? null : execution.currentStage());
            }
            String revisionId = revision.revisionId();
            scheduleNative(revisionId);
            return new MarkdownResumeResult(document, revision, null, "EXTRACTION", null);
        }
        if (!hasText(revision.markdownText())) {
            MarkdownPipelineOptions previous = readOptions(revision.optionsJson());
            MarkdownPipelineOptions options = request == null ? previous : merge(previous, request);
            MarkdownExtractionResult restarted = create(new MarkdownExtractionRequest(
                    revision.sourceAttachmentId(), options, true, "resume"));
            return new MarkdownResumeResult(restarted.document(), restarted.revision(), null,
                    "EXTRACTION", null);
        }

        MarkdownPipelineOptions previous = readOptions(revision.optionsJson());
        MarkdownPipelineOptions options = request == null ? previous : merge(previous, request);

        if (request != null) {
            String newOptionsJson = writeOptions(options);
            String newOptionsHash = hash(newOptionsJson.getBytes(StandardCharsets.UTF_8));
            revision = new MarkdownRevision(
                    revision.revisionId(), revision.documentId(), revision.sourceAttachmentId(),
                    revision.resultAttachmentId(), revision.documentConvertJobId(), revision.extractorType(),
                    revision.extractorVersion(),
                    newOptionsJson, newOptionsHash, revision.sourceContentHash(), revision.contentHash(),
                    revision.markdownText(),
                    revision.sourceFileName(), revision.sourceFormat(), revision.sourceObjectType(),
                    revision.sourceObjectId(),
                    revision.status(), revision.errorCode(), revision.errorMessage(), revision.createdAt(),
                    revision.startedAt(), revision.completedAt(), revision.updatedAt());
            repository.saveRevision(revision);
        }

        MarkdownPipelineStage fromStage = requestedStage == null ? resumeStage(revision, options) : requestedStage;
        if (fromStage == MarkdownPipelineStage.COMPLETED) {
            return new MarkdownResumeResult(document, revision,
                    repository.findPipelineExecution(revision.revisionId()).orElse(null),
                    "COMPLETED", MarkdownPipelineStage.COMPLETED);
        }
        validateEnabled(fromStage, options);
        assertTaskAvailable("pipeline:" + revision.revisionId());
        MarkdownPipelineExecution execution = pendingExecution(revision, fromStage);
        repository.savePipelineExecution(execution);
        schedulePipeline(revision, fromStage);
        return new MarkdownResumeResult(document, revision, execution, "PIPELINE", fromStage);
    }

    private MarkdownPipelineOptions merge(MarkdownPipelineOptions previous, MarkdownResumeOptions request) {
        boolean runChunking = request.runChunking() != null ? request.runChunking() : previous.runChunking();
        boolean runRagIndex = request.runRagIndex() != null ? request.runRagIndex() : previous.runRagIndex();
        boolean runSkillExtraction = request.runSkillExtraction() != null ? request.runSkillExtraction()
                : previous.runSkillExtraction();

        String chunkingStrategy = request.chunkingStrategy() != null ? request.chunkingStrategy()
                : previous.chunkingStrategy();
        Integer chunkMaxSize = request.chunkMaxSize() != null ? request.chunkMaxSize() : previous.chunkMaxSize();
        Integer chunkOverlap = request.chunkOverlap() != null ? request.chunkOverlap() : previous.chunkOverlap();
        String chunkUnit = request.chunkUnit() != null ? request.chunkUnit() : previous.chunkUnit();
        String blockifyLlmProvider = request.blockifyLlmProvider() != null
                ? request.blockifyLlmProvider()
                : previous.blockifyLlmProvider();
        String blockifyLlmModel = request.blockifyLlmModel() != null
                ? request.blockifyLlmModel()
                : previous.blockifyLlmModel();
        Boolean blockifyPiiMaskingEnabled = request.blockifyPiiMaskingEnabled() != null
                ? request.blockifyPiiMaskingEnabled()
                : previous.blockifyPiiMaskingEnabled();

        String embeddingProfileId = request.embeddingProfileId() != null ? request.embeddingProfileId()
                : previous.embeddingProfileId();
        String embeddingProvider = request.embeddingProvider() != null ? request.embeddingProvider()
                : previous.embeddingProvider();
        String embeddingModel = request.embeddingModel() != null ? request.embeddingModel() : previous.embeddingModel();
        Integer embeddingDimension = request.embeddingDimension() != null ? request.embeddingDimension()
                : previous.embeddingDimension();
        String embeddingDeploymentId = request.embeddingDeploymentId() != null
                ? request.embeddingDeploymentId()
                : previous.embeddingDeploymentId();
        boolean useLlmKeywordExtraction = request.useLlmKeywordExtraction() != null
                ? request.useLlmKeywordExtraction()
                : previous.useLlmKeywordExtraction();
        String skillExtractionMode = request.skillExtractionMode() != null
                ? request.skillExtractionMode()
                : previous.skillExtractionMode();
        boolean generateSkillEmbeddings = request.generateSkillEmbeddings() != null
                ? request.generateSkillEmbeddings()
                : previous.generateSkillEmbeddings();
        String skillEmbeddingProvider = request.skillEmbeddingProvider() != null
                ? request.skillEmbeddingProvider()
                : previous.skillEmbeddingProvider();
        String skillEmbeddingModel = request.skillEmbeddingModel() != null
                ? request.skillEmbeddingModel()
                : previous.skillEmbeddingModel();
        Integer skillEmbeddingDimension = request.skillEmbeddingDimension() != null
                ? request.skillEmbeddingDimension()
                : previous.skillEmbeddingDimension();
        Boolean ocrRequired = request.ocrRequired() != null ? request.ocrRequired() : previous.ocrRequired();
        String ocrLanguage = request.ocrLanguage() != null ? request.ocrLanguage() : previous.ocrLanguage();
        String ocrMode = request.ocrMode() != null ? request.ocrMode() : previous.ocrMode();
        Boolean mathVisionCorrection = request.mathVisionCorrection() != null
                ? request.mathVisionCorrection()
                : previous.mathVisionCorrection();

        if (request.embeddingProfileId() != null && !request.embeddingProfileId().isBlank()) {
            embeddingDeploymentId = null;
            embeddingProvider = null;
            embeddingModel = null;
            embeddingDimension = null;
        }
        if (request.embeddingDeploymentId() != null && !request.embeddingDeploymentId().isBlank()) {
            embeddingProfileId = null;
            embeddingProvider = null;
            embeddingModel = null;
        }

        return new MarkdownPipelineOptions(
                runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                blockifyLlmProvider, blockifyLlmModel, blockifyPiiMaskingEnabled,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, skillExtractionMode, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension, ocrRequired, ocrLanguage,
                ocrMode, mathVisionCorrection,
                previous.requestedDocumentProfile(),
                previous.resolvedDocumentProfile(),
                previous.documentProfileVersion(),
                embeddingDeploymentId);
    }

    public MarkdownResumeResult reindexRag(
            String documentId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            boolean runSkillExtraction) {
        return reindexRag(
                documentId,
                null,
                embeddingProfileId,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                false,
                runSkillExtraction,
                null,
                false,
                null,
                null,
                null);
    }

    public MarkdownResumeResult reindexRag(
            String documentId,
            String embeddingDeploymentId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            boolean useLlmKeywordExtraction,
            boolean runSkillExtraction,
            String skillExtractionMode,
            boolean generateSkillEmbeddings,
            String skillEmbeddingProvider,
            String skillEmbeddingModel,
            Integer skillEmbeddingDimension) {
        MarkdownDocument document = requireDocument(documentId);
        MarkdownRevision source = latestRevision(documentId);
        if (source.status() != MarkdownRevisionStatus.COMPLETED || !hasText(source.markdownText())) {
            throw new IllegalStateException("Completed Markdown revision with text is required for RAG reindex");
        }

        MarkdownPipelineOptions previous = readOptions(source.optionsJson());
        boolean explicitSelection = hasText(embeddingDeploymentId)
                || hasText(embeddingProfileId)
                || hasText(embeddingProvider)
                || hasText(embeddingModel)
                || embeddingDimension != null;
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                true,
                true,
                runSkillExtraction,
                previous.chunkingStrategy(),
                previous.chunkMaxSize(),
                previous.chunkOverlap(),
                previous.chunkUnit(),
                previous.blockifyLlmProvider(),
                previous.blockifyLlmModel(),
                previous.blockifyPiiMaskingEnabled(),
                hasText(embeddingDeploymentId) ? null
                        : explicitSelection ? embeddingProfileId : previous.embeddingProfileId(),
                hasText(embeddingDeploymentId) ? null
                        : explicitSelection ? embeddingProvider : previous.embeddingProvider(),
                hasText(embeddingDeploymentId) ? null
                        : explicitSelection ? embeddingModel : previous.embeddingModel(),
                explicitSelection ? embeddingDimension : previous.embeddingDimension(),
                useLlmKeywordExtraction,
                skillExtractionMode,
                generateSkillEmbeddings,
                skillEmbeddingProvider,
                skillEmbeddingModel,
                skillEmbeddingDimension,
                previous.ocrRequired(),
                previous.ocrLanguage(),
                previous.ocrMode(),
                previous.mathVisionCorrection(),
                previous.requestedDocumentProfile(),
                previous.resolvedDocumentProfile(),
                previous.documentProfileVersion(),
                hasText(embeddingDeploymentId) ? embeddingDeploymentId
                        : explicitSelection ? null : previous.embeddingDeploymentId());
        String optionsJson = writeOptions(options);
        String optionsHash = hash(optionsJson.getBytes(StandardCharsets.UTF_8));
        Instant now = nextRevisionTime(source.updatedAt());
        String revisionId = "mrev-" + UUID.randomUUID();
        MarkdownRevision revision = new MarkdownRevision(
                revisionId,
                source.documentId(),
                source.sourceAttachmentId(),
                source.resultAttachmentId(),
                source.documentConvertJobId(),
                source.extractorType(),
                source.extractorVersion(),
                optionsJson,
                optionsHash,
                source.sourceContentHash(),
                source.contentHash(),
                source.markdownText(),
                source.sourceFileName(),
                source.sourceFormat(),
                source.sourceObjectType(),
                source.sourceObjectId(),
                MarkdownRevisionStatus.COMPLETED,
                null,
                null,
                now,
                now,
                now,
                now);
        MarkdownPipelineExecution execution = new MarkdownPipelineExecution(
                revisionId,
                MarkdownPipelineExecutionStatus.PENDING,
                MarkdownPipelineStage.CHUNKING,
                null,
                0,
                null,
                null,
                null,
                null,
                now);
        MarkdownRevision saved = transactions.required(() -> {
            MarkdownRevision value = repository.saveRevision(revision);
            repository.replaceLocators(value.revisionId(), copyLocators(source.revisionId(), value.revisionId()));
            repository.replaceResources(value.revisionId(), copyResources(source.revisionId(), value.revisionId()));
            repository.saveDocument(new MarkdownDocument(
                    document.documentId(),
                    document.sourceAttachmentId(),
                    value.revisionId(),
                    document.createdAt(),
                    now));
            repository.savePipelineExecution(execution);
            return value;
        });
        schedulePipeline(saved, MarkdownPipelineStage.CHUNKING);
        return new MarkdownResumeResult(
                requireDocument(documentId),
                saved,
                execution,
                "PIPELINE",
                MarkdownPipelineStage.CHUNKING);
    }

    public List<MarkdownLocator> getLocators(String documentId) {
        MarkdownDocument document = requireDocument(documentId);
        return document.currentRevisionId() == null ? List.of() : locatorsWithNormalizedProvenance(document.currentRevisionId());
    }

    public List<MarkdownLocator> getProvenance(String documentId) {
        return getLocators(documentId);
    }

    public List<MarkdownResource> getResources(String documentId) {
        MarkdownDocument document = requireDocument(documentId);
        return document.currentRevisionId() == null ? List.of()
                : repository.findResources(document.currentRevisionId());
    }

    public MarkdownExtractionResult reextract(String documentId, MarkdownPipelineOptions options, String requestedBy) {
        return reextract(documentId, options, false, requestedBy);
    }

    public MarkdownExtractionResult reextract(String documentId, MarkdownPipelineOptions options,
            boolean inheritOmittedExtractionQualityOptions, String requestedBy) {
        MarkdownDocument document = requireDocument(documentId);
        MarkdownPipelineOptions effectiveOptions = inheritOmittedExtractionQualityOptions
                ? inheritExtractionQualityOptions(documentId, options)
                : options;
        return create(new MarkdownExtractionRequest(
                document.sourceAttachmentId(), effectiveOptions, true, requestedBy));
    }

    public MarkdownExtractionResult reextract(String documentId, boolean runChunking, boolean runRagIndex,
            boolean runSkillExtraction, String requestedBy) {
        return reextract(documentId,
                new MarkdownPipelineOptions(runChunking, runRagIndex, runSkillExtraction), requestedBy);
    }

    private MarkdownPipelineOptions inheritExtractionQualityOptions(
            String documentId, MarkdownPipelineOptions requested) {
        return repository.findRevisions(documentId).stream()
                .map(revision -> readOptions(revision.optionsJson()))
                .filter(this::hasExplicitExtractionQualityOptions)
                .findFirst()
                .map(previous -> new MarkdownPipelineOptions(
                        requested.runChunking(), requested.runRagIndex(), requested.runSkillExtraction(),
                        requested.chunkingStrategy(), requested.chunkMaxSize(), requested.chunkOverlap(),
                        requested.chunkUnit(), requested.blockifyLlmProvider(), requested.blockifyLlmModel(),
                        requested.blockifyPiiMaskingEnabled(), requested.embeddingProfileId(),
                        requested.embeddingProvider(), requested.embeddingModel(), requested.embeddingDimension(),
                        requested.useLlmKeywordExtraction(), requested.skillExtractionMode(),
                        requested.generateSkillEmbeddings(), requested.skillEmbeddingProvider(),
                        requested.skillEmbeddingModel(), requested.skillEmbeddingDimension(),
                        previous.ocrRequired(), previous.ocrLanguage(), previous.ocrMode(),
                        previous.mathVisionCorrection(), previous.requestedDocumentProfile(), null, null,
                        requested.embeddingDeploymentId()))
                .orElse(requested);
    }

    private boolean hasExplicitExtractionQualityOptions(MarkdownPipelineOptions options) {
        return options.requestedDocumentProfile() != null
                || options.ocrRequired() != null
                || options.ocrLanguage() != null
                || options.ocrMode() != null
                || Boolean.TRUE.equals(options.mathVisionCorrection());
    }

    public MarkdownRevision cancel(String documentId) {
        MarkdownDocument document = requireDocument(documentId);
        MarkdownRevision revision = repository.findRevisions(documentId).stream()
                .filter(item -> !item.status().terminal())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active extraction revision"));
        if (revision.documentConvertJobId() != null) {
            conversionPort.cancel(revision.documentConvertJobId());
        }
        Instant now = clock.instant();
        return repository.saveRevision(copy(revision, MarkdownRevisionStatus.CANCELED, revision.markdownText(),
                revision.contentHash(), revision.resultAttachmentId(), revision.documentConvertJobId(),
                revision.extractorVersion(), null, null, revision.startedAt(), now, now));
    }

    private MarkdownRevision markRunning(MarkdownRevision revision) {
        Instant now = clock.instant();
        return repository.saveRevision(copy(revision, MarkdownRevisionStatus.RUNNING, null, null, null, null,
                revision.extractorVersion(), null, null, now, null, now));
    }

    private MarkdownRevision withConversionJob(MarkdownRevision revision, String jobId) {
        MarkdownRevision current = repository.findRevision(revision.revisionId()).orElse(revision);
        if (current.status().terminal()) {
            return current;
        }
        return repository.saveRevision(copy(current, current.status(), current.markdownText(),
                current.contentHash(), current.resultAttachmentId(), jobId, current.extractorVersion(),
                current.errorCode(), current.errorMessage(), current.startedAt(), current.completedAt(),
                clock.instant()));
    }

    private MarkdownRevision complete(MarkdownRevision revision, Long resultAttachmentId, String markdown,
            String extractorVersion, List<MarkdownLocator> locators, List<MarkdownResource> resources) {
        Instant now = clock.instant();
        MarkdownPipelineOptions options = readOptions(revision.optionsJson());
        MarkdownNormalizationPort.NormalizationResult normalization = normalizationPort.normalize(
                new MarkdownNormalizationPort.NormalizationRequest(
                        revision.revisionId(), revision.sourceFormat(), revision.sourceFileName(), markdown,
                        locators, resources, normalizationSource(revision),
                        options.requestedDocumentProfile(), options.resolvedDocumentProfile(),
                        options.documentProfileVersion()));
        String normalized = normalization.markdown();
        if (normalized.isBlank()) {
            throw new NoTextExtractedException("Extracted markdown text is blank");
        }
        normalized = withPagePreviewLinks(revision.documentId(), normalized);
        MarkdownRevision completed = repository.saveRevision(copy(revision, MarkdownRevisionStatus.COMPLETED,
                normalized, hash(normalized.getBytes(StandardCharsets.UTF_8)), resultAttachmentId,
                revision.documentConvertJobId(), normalize(extractorVersion, revision.extractorVersion()),
                null, null, revision.startedAt(), now, now));
        repository.replaceLocators(completed.revisionId(),
                locatorsWithNormalizedProvenance(completed.revisionId(), normalization.locators(),
                        normalization.resources()));
        repository.replaceResources(completed.revisionId(), normalization.resources());
        MarkdownDocument document = requireDocument(completed.documentId());
        repository.saveDocument(new MarkdownDocument(document.documentId(), document.sourceAttachmentId(),
                completed.revisionId(), document.createdAt(), now));
        return completed;
    }

    private String normalizationSource(MarkdownRevision revision) {
        if ("PANDOC".equalsIgnoreCase(revision.extractorType())) {
            return "PANDOC_MARKDOWN";
        }
        if ("TEXTRACT".equalsIgnoreCase(revision.extractorType())) {
            return "NATIVE_PARSED_FILE";
        }
        return "MARKDOWN_FALLBACK";
    }

    private MarkdownRevision fail(MarkdownRevision revision, String errorCode, String errorMessage) {
        Instant now = clock.instant();
        return repository.saveRevision(copy(revision, MarkdownRevisionStatus.FAILED, revision.markdownText(),
                revision.contentHash(), revision.resultAttachmentId(), revision.documentConvertJobId(),
                revision.extractorVersion(), normalize(errorCode, "EXTRACTION_FAILED"),
                sanitize(errorMessage), revision.startedAt(), now, now));
    }

    private void processNative(String revisionId) {
        MarkdownRevision revision = repository.findRevision(revisionId).orElse(null);
        if (revision == null || revision.status().terminal()) {
            return;
        }
        if (recoverCompletedNativeExtractParts(revision) != null) {
            return;
        }
        try {
            MarkdownSourcePort.MarkdownSource source = sourcePort.load(revision.sourceAttachmentId());
            MarkdownNativeExtractorPort.NativeExtraction extracted = nativeExtractor.extract(source, revisionId);
            MarkdownRevision completed = transactions.required(() -> {
                repository.replaceExtractParts(revision.revisionId(), extracted.extractParts());
                if (!hasText(extracted.markdown()) && hasText(extracted.errorCode())) {
                    fail(revision, extracted.errorCode(), extracted.errorMessage());
                    return null;
                }
                MarkdownRevision value = complete(revision, null, extracted.markdown(), revision.extractorVersion(),
                        extracted.locators(), extracted.resources());
                preparePipeline(value);
                return value;
            });
            if (completed != null) {
                schedulePreparedPipeline(completed);
            }
        } catch (RuntimeException ex) {
            transactions.required(() -> fail(revision, extractionErrorCode(ex, "EXTRACTION_FAILED"), ex.getMessage()));
        }
    }

    private MarkdownRevision recoverCompletedNativeExtractParts(MarkdownRevision revision) {
        if (!"TEXTRACT".equalsIgnoreCase(revision.extractorType())) {
            return null;
        }
        String markdown = completedExtractPartsMarkdown(revision.revisionId());
        if (!hasText(markdown)) {
            return null;
        }
        MarkdownRevision completed = transactions.required(() -> {
            MarkdownRevision current = repository.findRevision(revision.revisionId()).orElse(null);
            if (current == null || current.status().terminal()) {
                return current != null && current.status() == MarkdownRevisionStatus.COMPLETED ? current : null;
            }
            MarkdownRevision value = complete(current, current.resultAttachmentId(), markdown,
                    current.extractorVersion(), List.of(), List.of());
            preparePipeline(value);
            return value;
        });
        if (completed != null && completed.status() == MarkdownRevisionStatus.COMPLETED) {
            schedulePreparedPipeline(completed);
        }
        return completed;
    }

    private String completedExtractPartsMarkdown(String revisionId) {
        List<MarkdownExtractPart> parts = repository.findExtractParts(revisionId);
        if (parts.isEmpty()) {
            return null;
        }
        boolean completed = parts.stream()
                .allMatch(part -> "COMPLETED".equalsIgnoreCase(part.status()));
        if (!completed) {
            return null;
        }
        List<String> markdownParts = parts.stream()
                .sorted(Comparator.comparingInt(MarkdownExtractPart::pageFrom)
                        .thenComparingInt(MarkdownExtractPart::pageTo))
                .map(MarkdownExtractPart::markdownText)
                .filter(this::hasText)
                .toList();
        return markdownParts.isEmpty() ? null : String.join("\n\n", markdownParts);
    }

    private String extractionErrorCode(RuntimeException ex, String fallback) {
        return ex instanceof NoTextExtractedException ? "NO_TEXT_EXTRACTED" : fallback;
    }

    private MarkdownExtractionResult fallbackToNative(MarkdownDocument document, MarkdownRevision revision,
            String errorCode, String errorMessage) {
        String revisionId = revision.revisionId();
        String taskKey = extractionTaskKey(revisionId);
        if (!activeTasks.add(taskKey)) {
            MarkdownRevision current = repository.findRevision(revisionId).orElse(revision);
            return new MarkdownExtractionResult(requireDocument(document.documentId()), current, false);
        }
        try {
            MarkdownRevision current = repository.findRevision(revisionId).orElse(revision);
            if (current.status().terminal()) {
                activeTasks.remove(taskKey);
                return new MarkdownExtractionResult(requireDocument(document.documentId()), current, false);
            }
            MarkdownRevision nativeRevision = switchToNativeFallback(current, errorCode, errorMessage);
            executeReservedTask(taskKey, () -> processNative(revisionId));
            current = repository.findRevision(revisionId).orElse(nativeRevision);
            return new MarkdownExtractionResult(requireDocument(document.documentId()), current, false);
        } catch (RuntimeException ex) {
            activeTasks.remove(taskKey);
            throw ex;
        }
    }

    private MarkdownRevision switchToNativeFallback(MarkdownRevision revision, String errorCode, String errorMessage) {
        Instant now = clock.instant();
        return repository.saveRevision(new MarkdownRevision(revision.revisionId(), revision.documentId(),
                revision.sourceAttachmentId(), null, revision.documentConvertJobId(), "TEXTRACT", "native",
                revision.optionsJson(), revision.optionsHash(), revision.sourceContentHash(), null, null,
                revision.sourceFileName(), revision.sourceFormat(), revision.sourceObjectType(), revision.sourceObjectId(),
                MarkdownRevisionStatus.RUNNING, normalize(errorCode, "PANDOC_CONVERSION_FAILED"),
                sanitize(errorMessage), revision.createdAt(), revision.startedAt(), null, now));
    }

    private static final class NoTextExtractedException extends RuntimeException {
        private NoTextExtractedException(String message) {
            super(message);
        }
    }

    private void scheduleNative(String revisionId) {
        scheduleTask(extractionTaskKey(revisionId), () -> processNative(revisionId));
    }

    private String extractionTaskKey(String revisionId) {
        return "extraction:" + revisionId;
    }

    private void prepareAndSchedulePipeline(MarkdownRevision revision) {
        preparePipeline(revision);
        schedulePreparedPipeline(revision);
    }

    private void preparePipeline(MarkdownRevision revision) {
        MarkdownPipelineOptions options = readOptions(revision.optionsJson());
        MarkdownPipelineStage first = firstStage(options);
        if (first == MarkdownPipelineStage.COMPLETED) {
            Instant now = clock.instant();
            repository.savePipelineExecution(new MarkdownPipelineExecution(
                    revision.revisionId(), MarkdownPipelineExecutionStatus.COMPLETED,
                    MarkdownPipelineStage.COMPLETED, MarkdownPipelineStage.COMPLETED,
                    0, null, null, null, now, now));
            return;
        }
        repository.savePipelineExecution(pendingExecution(revision, first));
    }

    private void schedulePreparedPipeline(MarkdownRevision revision) {
        MarkdownPipelineStage first = firstStage(readOptions(revision.optionsJson()));
        if (first != MarkdownPipelineStage.COMPLETED) {
            schedulePipeline(revision, first);
        }
    }

    private void schedulePipeline(MarkdownRevision revision, MarkdownPipelineStage fromStage) {
        scheduleTask("pipeline:" + revision.revisionId(), () -> runPipeline(revision, fromStage));
    }

    private void scheduleTask(String taskKey, Runnable task) {
        if (!activeTasks.add(taskKey)) {
            throw new IllegalStateException("Markdown task is already running: " + taskKey);
        }
        executeReservedTask(taskKey, task);
    }

    private void executeReservedTask(String taskKey, Runnable task) {
        try {
            taskExecutor.executeAfterCommit(() -> {
                try {
                    task.run();
                } finally {
                    activeTasks.remove(taskKey);
                }
            });
        } catch (RuntimeException ex) {
            activeTasks.remove(taskKey);
            throw ex;
        }
    }

    private void assertTaskAvailable(String taskKey) {
        if (activeTasks.contains(taskKey)) {
            throw new IllegalStateException("Markdown task is already running: " + taskKey);
        }
    }

    private void runPipeline(MarkdownRevision revision, MarkdownPipelineStage fromStage) {
        MarkdownPipelineOptions options = readOptions(revision.optionsJson());
        AtomicReference<MarkdownPipelineStage> current = new AtomicReference<>(fromStage);
        MarkdownPipelineExecution previous = repository.findPipelineExecution(revision.revisionId())
                .orElse(pendingExecution(revision, fromStage));
        AtomicReference<MarkdownPipelineStage> lastCompleted = new AtomicReference<>(previous.lastCompletedStage());
        Instant started = clock.instant();
        repository.savePipelineExecution(new MarkdownPipelineExecution(
                revision.revisionId(), MarkdownPipelineExecutionStatus.RUNNING, fromStage,
                previous.lastCompletedStage(), previous.attemptCount() + 1, null, null,
                started, null, started));
        try {
            pipelinePort.process(revision, options, fromStage, completedStage -> {
                MarkdownPipelineStage next = nextStage(completedStage, options);
                current.set(next);
                lastCompleted.set(completedStage);
                Instant now = clock.instant();
                repository.savePipelineExecution(new MarkdownPipelineExecution(
                        revision.revisionId(), MarkdownPipelineExecutionStatus.RUNNING, next,
                        completedStage, previous.attemptCount() + 1, null, null,
                        started, null, now));
            });
            Instant now = clock.instant();
            repository.savePipelineExecution(new MarkdownPipelineExecution(
                    revision.revisionId(), MarkdownPipelineExecutionStatus.COMPLETED,
                    MarkdownPipelineStage.COMPLETED, lastEnabledStage(options),
                    previous.attemptCount() + 1, null, null, started, now, now));
        } catch (RuntimeException ex) {
            Instant now = clock.instant();
            repository.savePipelineExecution(new MarkdownPipelineExecution(
                    revision.revisionId(), MarkdownPipelineExecutionStatus.FAILED, current.get(),
                    lastCompleted.get(), previous.attemptCount() + 1,
                    "PIPELINE_FAILED", sanitize(ex.getMessage()), started, now, now));
        } catch (Error error) {
            Instant now = clock.instant();
            repository.savePipelineExecution(new MarkdownPipelineExecution(
                    revision.revisionId(), MarkdownPipelineExecutionStatus.FAILED, current.get(),
                    lastCompleted.get(), previous.attemptCount() + 1,
                    "PIPELINE_ERROR", sanitize(error.getClass().getSimpleName() + ": " + error.getMessage()),
                    started, now, now));
            throw error;
        }
    }

    private void recoverStalePipelineExecutions() {
        Instant now = clock.instant();
        repository.recoverStalePipelineExecutions(
                now.minusSeconds(STALE_PIPELINE_RECOVERY_MINUTES * 60L),
                now);
    }

    private int pageCount(String revisionId) {
        return repository.findExtractParts(revisionId).stream()
                .mapToInt(part -> Math.max(part.pageFrom(), part.pageTo()))
                .max()
                .orElseGet(() -> (int) repository.findLocators(revisionId).stream()
                        .filter(locator -> "PAGE".equalsIgnoreCase(locator.locatorType()))
                        .count());
    }

    private int embeddingRequests(int chunkCount, int batchSize) {
        if (chunkCount <= 0) {
            return 0;
        }
        int effectiveBatchSize = Math.max(1, batchSize);
        return (int) Math.ceil((double) chunkCount / effectiveBatchSize);
    }

    private MarkdownPipelineEstimate.EmbeddingSelection embeddingSelection(MarkdownPipelineOptions options) {
        if (options == null || (options.embeddingProfileId() == null
                && options.embeddingProvider() == null
                && options.embeddingModel() == null
                && options.embeddingDimension() == null)) {
            return null;
        }
        return new MarkdownPipelineEstimate.EmbeddingSelection(
                options.embeddingProfileId(),
                options.embeddingProvider(),
                options.embeddingModel(),
                options.embeddingDimension());
    }

    private RecommendedEstimate recommendedEstimate(
            MarkdownRevision revision,
            MarkdownPipelineOptions current,
            int currentChunkCount,
            int embeddingBatchSize) {
        if (currentChunkCount <= ESTIMATE_TARGET_CHUNKS) {
            return new RecommendedEstimate(current, currentChunkCount,
                    embeddingRequests(currentChunkCount, embeddingBatchSize));
        }
        int overlap = current.chunkOverlap() == null ? 150 : current.chunkOverlap();
        String strategy = current.chunkingStrategy();
        String unit = current.chunkUnit() == null ? "CHARACTER" : current.chunkUnit();
        int[] candidates = { 2000, 3000, 4000, 6000 };
        RecommendedEstimate best = new RecommendedEstimate(current, currentChunkCount,
                embeddingRequests(currentChunkCount, embeddingBatchSize));
        for (int maxSize : candidates) {
            int candidateOverlap = Math.min(overlap, maxSize - 1);
            MarkdownPipelineOptions candidate = new MarkdownPipelineOptions(
                    current.runChunking(),
                    current.runRagIndex(),
                    current.runSkillExtraction(),
                    strategy,
                    maxSize,
                    candidateOverlap,
                    unit,
                    current.blockifyLlmProvider(),
                    current.blockifyLlmModel(),
                    current.blockifyPiiMaskingEnabled(),
                    current.embeddingProfileId(),
                    current.embeddingProvider(),
                    current.embeddingModel(),
                    current.embeddingDimension(),
                    current.useLlmKeywordExtraction(),
                    current.skillExtractionMode(),
                    current.generateSkillEmbeddings(),
                    current.skillEmbeddingProvider(),
                    current.skillEmbeddingModel(),
                    current.skillEmbeddingDimension(),
                    current.ocrRequired(),
                    current.ocrLanguage(),
                    current.ocrMode(),
                    current.mathVisionCorrection());
            int chunks = pipelinePort.estimateChunkCount(revision, candidate);
            best = new RecommendedEstimate(candidate, chunks, embeddingRequests(chunks, embeddingBatchSize));
            if (chunks <= ESTIMATE_TARGET_CHUNKS) {
                break;
            }
        }
        return best;
    }

    private RecommendedEstimate heuristicRecommendedEstimate(
            MarkdownPipelineOptions current,
            int markdownLengthEstimate,
            int currentChunkCount,
            int embeddingBatchSize) {
        if (currentChunkCount <= ESTIMATE_TARGET_CHUNKS) {
            return new RecommendedEstimate(current, currentChunkCount,
                    embeddingRequests(currentChunkCount, embeddingBatchSize));
        }
        int overlap = current.chunkOverlap() == null ? 150 : current.chunkOverlap();
        String strategy = current.chunkingStrategy();
        String unit = current.chunkUnit() == null ? "CHARACTER" : current.chunkUnit();
        int[] candidates = { 2000, 3000, 4000, 6000 };
        RecommendedEstimate best = new RecommendedEstimate(current, currentChunkCount,
                embeddingRequests(currentChunkCount, embeddingBatchSize));
        for (int maxSize : candidates) {
            int candidateOverlap = Math.min(overlap, maxSize - 1);
            MarkdownPipelineOptions candidate = new MarkdownPipelineOptions(
                    current.runChunking(),
                    current.runRagIndex(),
                    current.runSkillExtraction(),
                    strategy,
                    maxSize,
                    candidateOverlap,
                    unit,
                    current.blockifyLlmProvider(),
                    current.blockifyLlmModel(),
                    current.blockifyPiiMaskingEnabled(),
                    current.embeddingProfileId(),
                    current.embeddingProvider(),
                    current.embeddingModel(),
                    current.embeddingDimension(),
                    current.useLlmKeywordExtraction(),
                    current.skillExtractionMode(),
                    current.generateSkillEmbeddings(),
                    current.skillEmbeddingProvider(),
                    current.skillEmbeddingModel(),
                    current.skillEmbeddingDimension(),
                    current.ocrRequired(),
                    current.ocrLanguage(),
                    current.ocrMode(),
                    current.mathVisionCorrection());
            int chunks = heuristicChunkCount(markdownLengthEstimate, candidate);
            best = new RecommendedEstimate(candidate, chunks, embeddingRequests(chunks, embeddingBatchSize));
            if (chunks <= ESTIMATE_TARGET_CHUNKS) {
                break;
            }
        }
        return best;
    }

    private int heuristicChunkCount(int markdownLengthEstimate, MarkdownPipelineOptions options) {
        if (markdownLengthEstimate <= 0) {
            return 0;
        }
        int maxSize = options.chunkMaxSize() == null ? 1000 : options.chunkMaxSize();
        int overlap = options.chunkOverlap() == null ? 0 : Math.min(options.chunkOverlap(), maxSize - 1);
        int effectiveSize = Math.max(1, maxSize - overlap);
        return Math.max(1, (int) Math.ceil((double) markdownLengthEstimate / effectiveSize));
    }

    private List<MarkdownPipelineEstimate.Warning> estimateWarnings(int markdownLength, int pageCount, int chunkCount) {
        List<MarkdownPipelineEstimate.Warning> warnings = new ArrayList<>();
        if (markdownLength >= 10 * 1024 * 1024) {
            warnings.add(new MarkdownPipelineEstimate.Warning(
                    "LARGE_MARKDOWN",
                    "Markdown text is larger than 10MB. Use the recommended chunking options before indexing."));
        }
        if (pageCount >= 100) {
            warnings.add(new MarkdownPipelineEstimate.Warning(
                    "MANY_PAGES",
                    "Document has 100 or more pages. Indexing may take a long time."));
        }
        if (chunkCount > ESTIMATE_TARGET_CHUNKS) {
            warnings.add(new MarkdownPipelineEstimate.Warning(
                    "MANY_CHUNKS",
                    "Estimated chunk count is high. Recommended chunking options reduce embedding requests."));
        }
        return List.copyOf(warnings);
    }

    private String riskLevel(int chunkCount) {
        if (chunkCount >= 5000) {
            return "VERY_HIGH";
        }
        if (chunkCount >= 1500) {
            return "HIGH";
        }
        if (chunkCount >= 500) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private record RecommendedEstimate(
            MarkdownPipelineOptions options,
            int estimatedChunkCount,
            int estimatedEmbeddingRequests) {

        MarkdownPipelineEstimate.RecommendedChunking toResponse() {
            return new MarkdownPipelineEstimate.RecommendedChunking(
                    options.chunkingStrategy(),
                    options.chunkMaxSize(),
                    options.chunkOverlap(),
                    options.chunkUnit(),
                    estimatedChunkCount,
                    estimatedEmbeddingRequests);
        }
    }

    private MarkdownRevision latestRevision(String documentId) {
        requireDocument(documentId);
        return repository.findRevisions(documentId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No markdown revision: " + documentId));
    }

    private MarkdownPipelineExecution pendingExecution(MarkdownRevision revision, MarkdownPipelineStage stage) {
        MarkdownPipelineExecution previous = repository.findPipelineExecution(revision.revisionId()).orElse(null);
        MarkdownPipelineStage lastCompleted = previous == null ? null : previous.lastCompletedStage();
        if (lastCompleted != null && lastCompleted.ordinal() >= stage.ordinal()) {
            lastCompleted = previousStage(stage);
        }
        return new MarkdownPipelineExecution(revision.revisionId(), MarkdownPipelineExecutionStatus.PENDING,
                stage, lastCompleted,
                previous == null ? 0 : previous.attemptCount(), null, null,
                null, null, clock.instant());
    }

    private MarkdownPipelineExecution legacyPipelineExecution(MarkdownRevision revision) {
        MarkdownPipelineOptions options = readOptions(revision.optionsJson());
        MarkdownPipelineStage first = firstStage(options);
        if (first == MarkdownPipelineStage.COMPLETED) {
            return new MarkdownPipelineExecution(
                    revision.revisionId(), MarkdownPipelineExecutionStatus.COMPLETED,
                    MarkdownPipelineStage.COMPLETED, MarkdownPipelineStage.COMPLETED,
                    0, null, null, revision.startedAt(), revision.completedAt(), revision.updatedAt());
        }
        MarkdownPipelineExecutionStatus status = revision.status() == MarkdownRevisionStatus.COMPLETED
                ? MarkdownPipelineExecutionStatus.UNKNOWN
                : MarkdownPipelineExecutionStatus.PENDING;
        return new MarkdownPipelineExecution(
                revision.revisionId(), status, first, null, 0,
                status == MarkdownPipelineExecutionStatus.UNKNOWN ? "PIPELINE_HISTORY_UNAVAILABLE" : null,
                status == MarkdownPipelineExecutionStatus.UNKNOWN
                        ? "Pipeline execution history was not recorded for this legacy revision"
                        : null,
                null, null, revision.updatedAt());
    }

    private MarkdownPipelineStage previousStage(MarkdownPipelineStage stage) {
        return switch (stage) {
            case CHUNKING -> null;
            case RAG_INDEX -> MarkdownPipelineStage.CHUNKING;
            case SKILL_EXTRACTION -> MarkdownPipelineStage.RAG_INDEX;
            case COMPLETED -> MarkdownPipelineStage.SKILL_EXTRACTION;
        };
    }

    private MarkdownPipelineStage resumeStage(MarkdownRevision revision, MarkdownPipelineOptions options) {
        return repository.findPipelineExecution(revision.revisionId())
                .map(MarkdownPipelineExecution::currentStage)
                .orElseGet(() -> firstStage(options));
    }

    private MarkdownPipelineStage firstStage(MarkdownPipelineOptions options) {
        if (options.runChunking()) {
            return MarkdownPipelineStage.CHUNKING;
        }
        if (options.runRagIndex()) {
            return MarkdownPipelineStage.RAG_INDEX;
        }
        if (options.runSkillExtraction()) {
            return MarkdownPipelineStage.SKILL_EXTRACTION;
        }
        return MarkdownPipelineStage.COMPLETED;
    }

    private MarkdownPipelineStage nextStage(MarkdownPipelineStage completed, MarkdownPipelineOptions options) {
        if (completed.ordinal() < MarkdownPipelineStage.RAG_INDEX.ordinal() && options.runRagIndex()) {
            return MarkdownPipelineStage.RAG_INDEX;
        }
        if (completed.ordinal() < MarkdownPipelineStage.SKILL_EXTRACTION.ordinal()
                && options.runSkillExtraction()) {
            return MarkdownPipelineStage.SKILL_EXTRACTION;
        }
        return MarkdownPipelineStage.COMPLETED;
    }

    private MarkdownPipelineStage lastEnabledStage(MarkdownPipelineOptions options) {
        if (options.runSkillExtraction()) {
            return MarkdownPipelineStage.SKILL_EXTRACTION;
        }
        if (options.runRagIndex()) {
            return MarkdownPipelineStage.RAG_INDEX;
        }
        if (options.runChunking()) {
            return MarkdownPipelineStage.CHUNKING;
        }
        return MarkdownPipelineStage.COMPLETED;
    }

    private void validateEnabled(MarkdownPipelineStage stage, MarkdownPipelineOptions options) {
        boolean enabled = switch (stage) {
            case CHUNKING -> options.runChunking();
            case RAG_INDEX -> options.runRagIndex();
            case SKILL_EXTRACTION -> options.runSkillExtraction();
            case COMPLETED -> true;
        };
        if (!enabled) {
            throw new IllegalArgumentException("Pipeline stage is not enabled: " + stage);
        }
    }

    private String writeOptions(MarkdownExtractionRequest request) {
        return writeOptions(request.pipelineOptions());
    }

    private String writeOptions(MarkdownPipelineOptions options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize extraction options", ex);
        }
    }

    private List<MarkdownLocator> copyLocators(String sourceRevisionId, String targetRevisionId) {
        return repository.findLocators(sourceRevisionId).stream()
                .map(locator -> new MarkdownLocator(
                        "mloc-" + UUID.randomUUID(),
                        targetRevisionId,
                        locator.locatorType(),
                        locator.locatorNo(),
                        locator.title(),
                        locator.startOffset(),
                        locator.endOffset(),
                        locator.sourceRef(),
                        locator.metadataJson()))
                .toList();
    }

    private List<MarkdownLocator> locatorsWithNormalizedProvenance(String revisionId) {
        List<MarkdownLocator> locators = new ArrayList<>(repository.findLocators(revisionId));
        if (locators.stream().noneMatch(locator -> "NORMALIZED_BLOCK".equals(locator.locatorType()))) {
            locators.addAll(normalizedBlockLocators(revisionId));
        }
        return List.copyOf(locators);
    }

    private List<MarkdownLocator> locatorsWithNormalizedProvenance(String revisionId,
            List<MarkdownLocator> locators, List<MarkdownResource> resources) {
        List<MarkdownLocator> values = new ArrayList<>(locators == null ? List.of() : locators);
        if (values.stream().anyMatch(locator -> "NORMALIZED_BLOCK".equals(locator.locatorType()))) {
            return List.copyOf(values);
        }
        values.addAll(normalizedBlockLocators(revisionId, resources));
        return List.copyOf(values);
    }

    private List<MarkdownLocator> normalizedBlockLocators(String revisionId, List<MarkdownResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream()
                .filter(resource -> MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType()))
                .findFirst()
                .map(resource -> normalizedBlockLocators(revisionId, resource))
                .orElse(List.of());
    }

    private List<MarkdownLocator> normalizedBlockLocators(String revisionId) {
        return repository.findResources(revisionId).stream()
                .filter(resource -> MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType()))
                .findFirst()
                .map(resource -> normalizedBlockLocators(revisionId, resource))
                .orElse(List.of());
    }

    private List<MarkdownLocator> normalizedBlockLocators(String revisionId, MarkdownResource resource) {
        try {
            Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), MAP_TYPE);
            if (!"normalized-document-v1".equals(text(payload.get("schemaVersion")))) {
                return List.of();
            }
            Object document = payload.get("document");
            if (!(document instanceof Map<?, ?> documentMap)) {
                return List.of();
            }
            Object blocks = documentMap.get("blocks");
            if (!(blocks instanceof List<?> blockList)) {
                return List.of();
            }
            List<MarkdownLocator> locators = new ArrayList<>();
            for (Object item : blockList) {
                if (item instanceof Map<?, ?> block) {
                    normalizedBlockLocator(revisionId, locators.size(), block).ifPresent(locators::add);
                }
            }
            return List.copyOf(locators);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private java.util.Optional<MarkdownLocator> normalizedBlockLocator(
            String revisionId, int index, Map<?, ?> block) {
        Object metadata = block.get("metadata");
        Map<?, ?> metadataMap = metadata instanceof Map<?, ?> value ? value : Map.of();
        String sourceRef = text(firstPresent(block.get("sourceRef"), metadataMap.get("sourceRef")));
        Integer page = firstInteger(
                block.get("page"),
                metadataMap.get("page"),
                pageFromSourceRef(sourceRef));
        Integer slide = firstInteger(
                block.get("slide"),
                metadataMap.get("slide"),
                slideFromSourceRef(sourceRef));
        Object bbox = firstPresent(metadataMap.get("bbox"), metadataMap.get("boundingBox"));
        if (!hasText(sourceRef) && page == null && slide == null && bbox == null) {
            return java.util.Optional.empty();
        }

        Map<String, Object> locatorMetadata = new java.util.LinkedHashMap<>();
        putIfPresent(locatorMetadata, "schemaVersion", "normalized-document-v1");
        putIfPresent(locatorMetadata, "provenanceSource", "NORMALIZED_DOCUMENT");
        putIfPresent(locatorMetadata, "blockId", text(block.get("id")));
        putIfPresent(locatorMetadata, "blockType", text(block.get("type")));
        putIfPresent(locatorMetadata, "page", page);
        putIfPresent(locatorMetadata, "slide", slide);
        putIfPresent(locatorMetadata, "order", integer(block.get("order")));
        putIfPresent(locatorMetadata, "sourceRef", sourceRef);
        putIfPresent(locatorMetadata, "bbox", bbox);
        putIfPresent(locatorMetadata, "blockIds", block.get("blockIds"));
        putIfPresent(locatorMetadata, "confidence", block.get("confidence"));
        putIfPresent(locatorMetadata, "metadata", metadataMap);

        return java.util.Optional.of(new MarkdownLocator(
                "mloc-normalized-" + revisionId + "-" + index,
                revisionId,
                "NORMALIZED_BLOCK",
                page,
                title(block),
                0,
                0,
                sourceRef,
                writeJson(locatorMetadata),
                page,
                slide,
                bbox));
    }

    private String title(Map<?, ?> block) {
        String text = text(block.get("text"));
        if (text.isBlank()) {
            return text(block.get("type"), "NORMALIZED_BLOCK");
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 120 ? collapsed : collapsed.substring(0, 120);
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Object firstPresent(Object first, Object second) {
        return first == null ? second : first;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String string && string.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer firstInteger(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Integer integer = integer(value);
            if (integer != null) {
                return integer;
            }
        }
        return null;
    }

    private Integer pageFromSourceRef(String sourceRef) {
        if (!hasText(sourceRef)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("page\\[(\\d+)]").matcher(sourceRef);
        if (!matcher.find()) {
            return null;
        }
        return integer(matcher.group(1));
    }

    private Integer slideFromSourceRef(String sourceRef) {
        if (!hasText(sourceRef)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("slide\\[(\\d+)]").matcher(sourceRef);
        if (!matcher.find()) {
            return null;
        }
        return integer(matcher.group(1));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<MarkdownResource> copyResources(String sourceRevisionId, String targetRevisionId) {
        return repository.findResources(sourceRevisionId).stream()
                .map(resource -> new MarkdownResource(
                        "mres-" + UUID.randomUUID(),
                        targetRevisionId,
                        resource.resourceType(),
                        resource.name(),
                        resource.attachmentId(),
                        resource.metadataJson()))
                .toList();
    }

    private Instant nextRevisionTime(Instant previous) {
        Instant now = clock.instant();
        return previous == null || now.isAfter(previous) ? now : previous.plusMillis(1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MarkdownPipelineOptions readOptions(String json) {
        try {
            return objectMapper.readValue(json, MarkdownPipelineOptions.class);
        } catch (Exception ex) {
            try {
                Map<String, Object> legacy = objectMapper.readValue(json, MAP_TYPE);
                return new MarkdownPipelineOptions(
                        booleanValue(legacy.get("runChunking")),
                        booleanValue(legacy.get("runRagIndex")),
                        booleanValue(legacy.get("runSkillExtraction")));
            } catch (Exception ignored) {
                return MarkdownPipelineOptions.none();
            }
        }
    }

    private MarkdownDocument requireDocument(String documentId) {
        return repository.findDocument(documentId)
                .orElseThrow(() -> new MarkdownDocumentNotFoundException(
                        "Markdown document not found: " + documentId));
    }

    private MarkdownContent markdownContent(MarkdownRevision revision) {
        if (revision.status() != MarkdownRevisionStatus.COMPLETED || !hasText(revision.markdownText())) {
            throw new MarkdownContentUnavailableException(
                    "Completed Markdown content is not available for revision: " + revision.revisionId());
        }
        String markdown = withPagePreviewLinks(revision.documentId(), revision.markdownText());
        String contentHash = hash(markdown.getBytes(StandardCharsets.UTF_8));
        Path path = ensureMarkdownResultFile(revision, contentHash, markdown);
        long contentLength;
        try {
            contentLength = Files.size(path);
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException("Failed to read Markdown result file: " + path, ex);
        }
        return new MarkdownContent(
                revision.documentId(),
                revision.revisionId(),
                markdownFilename(revision),
                contentHash,
                path,
                contentLength);
    }

    private Path ensureMarkdownResultFile(MarkdownRevision revision, String contentHash, String markdown) {
        Path directory = markdownResultDirectory
                .resolve(contentHash.substring(0, Math.min(2, contentHash.length())))
                .resolve(contentHash);
        Path file = directory.resolve(revision.revisionId() + ".md");
        if (Files.exists(file)) {
            return file;
        }
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, revision.revisionId() + "-", ".tmp");
            try {
                Files.writeString(temp, markdown, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return file;
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException("Failed to write Markdown result file: " + file, ex);
        }
    }

    private String withPagePreviewLinks(String documentId, String markdown) {
        Matcher matcher = LOGICAL_IMAGE_REFERENCE.matcher(markdown);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String replacement = "](" + webBasePath + "/" + documentId + "/pages/" + matcher.group(1)
                    + "/preview)";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private boolean isPdf(MarkdownSourcePort.MarkdownSource source) {
        String contentType = source.contentType() == null ? "" : source.contentType().toLowerCase(java.util.Locale.ROOT);
        String filename = source.fileName() == null ? "" : source.fileName().toLowerCase(java.util.Locale.ROOT);
        return contentType.startsWith("application/pdf") || filename.endsWith(".pdf");
    }

    private Path previewPath(String sourceHash, int page, MarkdownPagePreviewBounds bounds) {
        String key = sourceHash + ":" + page + ":" + (bounds == null ? "page" : bounds.toString());
        String previewHash = hash(key.getBytes(StandardCharsets.UTF_8));
        return markdownResultDirectory.resolve("previews")
                .resolve(previewHash.substring(0, 2))
                .resolve(previewHash + ".png");
    }

    private void writePreviewFile(Path path, byte[] content) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), "preview-", ".tmp");
            try {
                Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (java.io.IOException ex) {
            throw new UncheckedIOException("Failed to cache PDF page preview: " + path, ex);
        }
    }

    private String normalizeWebBasePath(String value) {
        String normalized = hasText(value) ? value.trim() : DEFAULT_WEB_BASE_PATH;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String markdownFilename(MarkdownRevision revision) {
        String source = hasText(revision.sourceFileName()) ? revision.sourceFileName() : revision.revisionId();
        String sanitized = source.replaceAll("[\\\\/\\r\\n\\t]+", "_").trim();
        if (sanitized.isBlank()) {
            sanitized = revision.revisionId();
        }
        String lower = sanitized.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return sanitized;
        }
        int extension = sanitized.lastIndexOf('.');
        String base = extension > 0 ? sanitized.substring(0, extension) : sanitized;
        return base + ".md";
    }

    private MarkdownRevision findConversionRevision(String jobId, Long sourceAttachmentId) {
        return repository.findRevisionByConvertJobId(jobId)
                .orElseGet(() -> sourceAttachmentId == null ? null
                        : repository.findActiveRevisionBySourceAttachmentId(sourceAttachmentId).orElse(null));
    }

    private List<MarkdownLocator> headingLocators(String revisionId, String markdown) {
        record Heading(int number, String title, int start) {
        }
        List<Heading> headings = new ArrayList<>();
        int offset = 0;
        int section = 0;
        for (String line : markdown.split("\\n", -1)) {
            if (line.matches("^#{1,6}\\s+.+")) {
                section++;
                String title = line.replaceFirst("^#{1,6}\\s+", "").trim();
                headings.add(new Heading(section, title, offset));
            }
            offset += line.length() + 1;
        }
        List<MarkdownLocator> result = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            Heading heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : markdown.length();
            result.add(new MarkdownLocator("mloc-" + UUID.randomUUID(), revisionId, "SECTION",
                    heading.number(), heading.title(), heading.start(), end, null, "{}"));
        }
        return result;
    }

    private String sourceFormat(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        if (contentType != null && contentType.toLowerCase().contains("html")) {
            return "html";
        }
        if (contentType != null && contentType.toLowerCase().contains("wordprocessingml")) {
            return "docx";
        }
        if (contentType != null && contentType.equalsIgnoreCase("application/epub+zip")) {
            return "epub";
        }
        return "unknown";
    }

    private boolean shouldUsePandoc(String sourceFormat) {
        return sourceFormat != null && pandocSourceFormats.contains(sourceFormat.toLowerCase());
    }

    private Set<String> normalizeFormats(Set<String> formats) {
        if (formats == null || formats.isEmpty()) {
            return DEFAULT_PANDOC_SOURCE_FORMATS;
        }
        Set<String> normalized = new HashSet<>();
        for (String format : formats) {
            if (format != null && !format.isBlank()) {
                normalized.add(format.trim().toLowerCase());
            }
        }
        return normalized.isEmpty() ? DEFAULT_PANDOC_SOURCE_FORMATS : Set.copyOf(normalized);
    }

    private MarkdownRevision copy(MarkdownRevision source, MarkdownRevisionStatus status, String markdown,
            String contentHash, Long resultAttachmentId, String convertJobId, String extractorVersion,
            String errorCode, String errorMessage, Instant startedAt, Instant completedAt, Instant updatedAt) {
        return new MarkdownRevision(source.revisionId(), source.documentId(), source.sourceAttachmentId(),
                resultAttachmentId, convertJobId, source.extractorType(), extractorVersion,
                source.optionsJson(), source.optionsHash(), source.sourceContentHash(), contentHash, markdown,
                source.sourceFileName(), source.sourceFormat(), source.sourceObjectType(), source.sourceObjectId(),
                status, errorCode, errorMessage, source.createdAt(), startedAt, completedAt, updatedAt);
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String sanitize(String value) {
        String normalized = normalize(value, "Markdown extraction failed").replaceAll("https?://\\S+", "[url]");
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
