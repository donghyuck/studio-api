package studio.one.platform.markdown.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

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

@Transactional
public class MarkdownDocumentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final MarkdownRepository repository;
    private final MarkdownSourcePort sourcePort;
    private final MarkdownNativeExtractorPort nativeExtractor;
    private final MarkdownConversionPort conversionPort;
    private final MarkdownPipelinePort pipelinePort;
    private final MarkdownTaskExecutor taskExecutor;
    private final MarkdownTransactionOperations transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String pandocVersion;
    private final Set<String> activeTasks = ConcurrentHashMap.newKeySet();

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
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sourcePort = Objects.requireNonNull(sourcePort, "sourcePort");
        this.nativeExtractor = Objects.requireNonNull(nativeExtractor, "nativeExtractor");
        this.conversionPort = Objects.requireNonNull(conversionPort, "conversionPort");
        this.pipelinePort = pipelinePort == null ? MarkdownPipelinePort.noop() : pipelinePort;
        this.taskExecutor = taskExecutor == null ? MarkdownTaskExecutor.direct() : taskExecutor;
        this.transactions = transactions == null ? MarkdownTransactionOperations.direct() : transactions;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pandocVersion = normalize(pandocVersion, "pandoc");
    }

    public MarkdownExtractionResult create(MarkdownExtractionRequest request) {
        MarkdownSourcePort.MarkdownSource source = sourcePort.load(request.attachmentId());
        String sourceFormat = sourceFormat(source.fileName(), source.contentType());
        boolean pandoc = sourceFormat.equals("docx") || sourceFormat.equals("html");
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
            MarkdownConversionPort.ConversionSubmission submission =
                    conversionPort.submit(conversionJobId, source.attachmentId(), sourceFormat, request.requestedBy());
            revision = withConversionJob(revision, submission.jobId());
            if ("FAILED".equalsIgnoreCase(submission.status())) {
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

    public MarkdownPipelineExecution getPipelineExecution(String documentId) {
        MarkdownRevision revision = latestRevision(documentId);
        return repository.findPipelineExecution(revision.revisionId())
                .orElseGet(() -> legacyPipelineExecution(revision));
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
                    revision.resultAttachmentId(), revision.documentConvertJobId(), revision.extractorType(), revision.extractorVersion(),
                    newOptionsJson, newOptionsHash, revision.sourceContentHash(), revision.contentHash(), revision.markdownText(),
                    revision.sourceFileName(), revision.sourceFormat(), revision.sourceObjectType(), revision.sourceObjectId(),
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
        boolean runSkillExtraction = request.runSkillExtraction() != null ? request.runSkillExtraction() : previous.runSkillExtraction();

        String chunkingStrategy = request.chunkingStrategy() != null ? request.chunkingStrategy() : previous.chunkingStrategy();
        Integer chunkMaxSize = request.chunkMaxSize() != null ? request.chunkMaxSize() : previous.chunkMaxSize();
        Integer chunkOverlap = request.chunkOverlap() != null ? request.chunkOverlap() : previous.chunkOverlap();
        String chunkUnit = request.chunkUnit() != null ? request.chunkUnit() : previous.chunkUnit();

        String embeddingProfileId = request.embeddingProfileId() != null ? request.embeddingProfileId() : previous.embeddingProfileId();
        String embeddingProvider = request.embeddingProvider() != null ? request.embeddingProvider() : previous.embeddingProvider();
        String embeddingModel = request.embeddingModel() != null ? request.embeddingModel() : previous.embeddingModel();
        Integer embeddingDimension = request.embeddingDimension() != null ? request.embeddingDimension() : previous.embeddingDimension();
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

        if (request.embeddingProfileId() != null && !request.embeddingProfileId().isBlank()) {
            embeddingProvider = null;
            embeddingModel = null;
            embeddingDimension = null;
        }

        return new MarkdownPipelineOptions(
                runChunking, runRagIndex, runSkillExtraction,
                chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                embeddingProfileId, embeddingProvider, embeddingModel, embeddingDimension,
                useLlmKeywordExtraction, skillExtractionMode, generateSkillEmbeddings,
                skillEmbeddingProvider, skillEmbeddingModel, skillEmbeddingDimension);
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
        boolean explicitSelection = hasText(embeddingProfileId)
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
                explicitSelection ? embeddingProfileId : previous.embeddingProfileId(),
                explicitSelection ? embeddingProvider : previous.embeddingProvider(),
                explicitSelection ? embeddingModel : previous.embeddingModel(),
                explicitSelection ? embeddingDimension : previous.embeddingDimension(),
                useLlmKeywordExtraction,
                skillExtractionMode,
                generateSkillEmbeddings,
                skillEmbeddingProvider,
                skillEmbeddingModel,
                skillEmbeddingDimension);
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
        return document.currentRevisionId() == null ? List.of() : repository.findLocators(document.currentRevisionId());
    }

    public List<MarkdownResource> getResources(String documentId) {
        MarkdownDocument document = requireDocument(documentId);
        return document.currentRevisionId() == null ? List.of() : repository.findResources(document.currentRevisionId());
    }

    public MarkdownExtractionResult reextract(String documentId, MarkdownPipelineOptions options, String requestedBy) {
        MarkdownDocument document = requireDocument(documentId);
        return create(new MarkdownExtractionRequest(document.sourceAttachmentId(), options, true, requestedBy));
    }

    public MarkdownExtractionResult reextract(String documentId, boolean runChunking, boolean runRagIndex,
            boolean runSkillExtraction, String requestedBy) {
        return reextract(documentId,
                new MarkdownPipelineOptions(runChunking, runRagIndex, runSkillExtraction), requestedBy);
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
        String normalized = markdown == null ? "" : markdown;
        if (normalized.isBlank()) {
            throw new NoTextExtractedException("Extracted markdown text is blank");
        }
        MarkdownRevision completed = repository.saveRevision(copy(revision, MarkdownRevisionStatus.COMPLETED,
                normalized, hash(normalized.getBytes(StandardCharsets.UTF_8)), resultAttachmentId,
                revision.documentConvertJobId(), normalize(extractorVersion, revision.extractorVersion()),
                null, null, revision.startedAt(), now, now));
        repository.replaceLocators(completed.revisionId(), locators == null ? List.of() : locators);
        repository.replaceResources(completed.revisionId(), resources == null ? List.of() : resources);
        MarkdownDocument document = requireDocument(completed.documentId());
        repository.saveDocument(new MarkdownDocument(document.documentId(), document.sourceAttachmentId(),
                completed.revisionId(), document.createdAt(), now));
        return completed;
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

    private String extractionErrorCode(RuntimeException ex, String fallback) {
        return ex instanceof NoTextExtractedException ? "NO_TEXT_EXTRACTED" : fallback;
    }

    private static final class NoTextExtractedException extends RuntimeException {
        private NoTextExtractedException(String message) {
            super(message);
        }
    }

    private void scheduleNative(String revisionId) {
        scheduleTask("extraction:" + revisionId, () -> processNative(revisionId));
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
        AtomicReference<MarkdownPipelineStage> lastCompleted =
                new AtomicReference<>(previous.lastCompletedStage());
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
