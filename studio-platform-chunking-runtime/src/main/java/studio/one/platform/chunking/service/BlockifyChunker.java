package studio.one.platform.chunking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.core.NormalizedDocumentChunker;

public class BlockifyChunker implements NormalizedDocumentChunker {

    static final String SCHEMA_VERSION = "blockify-metadata-v1";
    static final String VALIDATION_RULE_VALIDATED = "RULE_VALIDATED";
    static final String VALIDATION_FALLBACK = "FALLBACK";
    static final String VALIDATION_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    static final String KEY_BLOCKIFY_LLM_PROVIDER = "blockifyLlmProvider";
    static final String KEY_BLOCKIFY_LLM_MODEL = "blockifyLlmModel";
    static final String KEY_BLOCKIFY_PII_MASKING_ENABLED = "blockifyPiiMaskingEnabled";
    static final String KEY_DETECTED_DOCUMENT_TYPE = "detectedDocumentType";
    static final String KEY_REQUESTED_DOCUMENT_TYPE = "requestedDocumentType";
    static final String KEY_DOCUMENT_TYPE_CONFIDENCE = "documentTypeConfidence";
    static final String KEY_DOCUMENT_TYPE_SIGNALS = "documentTypeSignals";
    static final String KEY_DOCUMENT_TYPE_REASON = "documentTypeReason";
    static final String KEY_BLOCKIFY_PROFILE = "blockifyProfile";
    static final String KEY_IDEA_BLOCK_SCHEMA_VERSION = "ideaBlockSchemaVersion";
    private static final String REASON_ANSWER_TOO_SHORT = "ANSWER_TOO_SHORT";
    private static final String REASON_ANSWER_HAS_NO_BODY = "ANSWER_HAS_NO_BODY";
    private static final String REASON_HEADING_ONLY = "HEADING_ONLY";
    private static final String REASON_EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND";
    private static final String REASON_GENERIC_QUESTION = "GENERIC_QUESTION";
    private static final String REASON_TOC_SECTION = "TOC_SECTION";
    private static final String GENERATION_MODE_RECOVERY_HEURISTIC = "RECOVERY_HEURISTIC";
    private static final String GENERATION_MODE_LOCAL_HEURISTIC = "LOCAL_HEURISTIC";
    private static final String REASON_LLM_CANDIDATE_LIMIT = "LLM_CANDIDATE_LIMIT";
    private static final String GENERATOR_MODEL_HEURISTIC = "heuristic-blockify-v1";
    private static final int LONG_SOURCE_BLOCK_SPLIT_FACTOR = 2;
    private static final int MAX_SPLIT_SOURCE_BLOCKS = 200;
    private static final String KEY_SOURCE_BLOCK_SPLIT = "sourceBlockSplit";
    private static final String KEY_SOURCE_BLOCK_PARENT_ID = "sourceBlockParentId";
    private static final String KEY_SOURCE_BLOCK_PART_INDEX = "sourceBlockPartIndex";
    private static final String KEY_SOURCE_BLOCK_PART_COUNT = "sourceBlockPartCount";
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern ARTICLE_HEADING = Pattern.compile(
            "^\\s*(?:\\[\\]\\{#[^}]+}\\s*)?(?:\\*\\*)?(제\\s*\\d+조\\s*\\([^)]*\\))(?:\\*\\*)?\\s*(.*)$");
    private static final Pattern FACT_TOKEN = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:년|월|일|시간|분|주|개월|일간|주간|개월간|%|퍼센트|점|회|명|원)?");

    private final ChunkingProperties.BlockifyProperties properties;
    private final StructureBasedChunker fallbackChunker;
    private final BlockifyGenerator generator;
    private final ExecutorService generationExecutor;
    private final BlockifyGenerator recoveryGenerator = new HeuristicBlockifyGenerator();
    private final BlockifyDocumentTypeClassifier documentTypeClassifier = new BlockifyDocumentTypeClassifier();
    private final BlockifyProfileResolver profileResolver = new BlockifyProfileResolver();
    private final BlockifyTypedFieldValidator typedFieldValidator = new BlockifyTypedFieldValidator();

    public BlockifyChunker(
            ChunkingProperties.BlockifyProperties properties,
            StructureBasedChunker fallbackChunker,
            BlockifyGenerator generator) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.fallbackChunker = Objects.requireNonNull(fallbackChunker, "fallbackChunker");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.generationExecutor = Executors.newFixedThreadPool(
                Math.max(1, properties.getGlobalConcurrency()), new BlockifyThreadFactory());
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.BLOCKIFY;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        NormalizedDocument document = NormalizedDocument.builder(context.sourceDocumentId())
                .plainText(context.text())
                .sourceFormat(context.contentType())
                .filename(context.filename())
                .blocks(blocksFromMarkdownText(context.sourceDocumentId(), context.text(), context.metadata()))
                .metadata(context.metadata())
                .build();
        return chunk(document, context);
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document, ChunkingContext context) {
        if (document == null || document.chunkableText().isBlank()) {
            return List.of();
        }
        document = withConfiguredDocumentType(document, context);
        BlockifyDocumentTypeClassification classification = documentTypeClassifier.classify(document, context);
        BlockifyProfile profile = profileResolver.resolve(classification);
        document = withProfileMetadata(document, classification, profile);
        List<Section> sections = splitSections(document);
        List<Chunk> chunks = new ArrayList<>();
        int order = 0;
        boolean externalGenerationUnavailable = false;
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            if (index >= properties.getMaxSectionsPerDocument()) {
                order = appendFallback(document, context, section, chunks, order, "MAX_SECTIONS_EXCEEDED");
                continue;
            }
            if (isHeadingOnly(section.blocks())) {
                continue;
            }
            if (isTocSection(section)) {
                continue;
            }
            List<Section> candidates = sourceBlockCandidates(document, section);
            if (candidates.isEmpty()) {
                order = appendFallback(document, context, section, chunks, order, "NO_SOURCE_BLOCK_CANDIDATES");
                continue;
            }
            if (containsTable(section.blocks())) {
                order = appendFallback(document, context, section, chunks, order, "TABLE_SECTION");
                continue;
            }
            int coveredBefore = coveredSourceBlockCount(chunks, section);
            for (Section candidate : candidates) {
                if (ChunkSizing.estimateTokens(content(candidate.blocks())) > properties.getMaxInputTokens()) {
                    order = appendFallback(document, context, candidate, chunks, order, "MAX_INPUT_TOKENS_EXCEEDED");
                    continue;
                }
                if (externalGenerationUnavailable) {
                    int recoveredOrder = appendRecoveryBlockify(document, context, candidate, chunks, order,
                            "LLM_GENERATION_UNAVAILABLE");
                    order = recoveredOrder == order
                            ? appendFallback(document, context, candidate, chunks, order, "LLM_GENERATION_UNAVAILABLE")
                            : recoveredOrder;
                    continue;
                }
                try {
                    BlockifyGenerationRequest generationRequest = new BlockifyGenerationRequest(
                            effectiveSourceDocumentId(document, context),
                            candidate.sectionId(),
                            candidate.headingPath(),
                            candidate.blocks(),
                            properties.getPromptVersion(),
                            effectiveLlmProvider(document, context),
                            effectiveLlmModel(document, context),
                            effectiveGeneratorModel(document, context),
                            properties.getTemperature(),
                            properties.getTopP(),
                            effectivePiiMaskingEnabled(document, context),
                            properties.getMaxBlocksPerSection(),
                            profile.documentType(),
                            profile.profileId(),
                            profile.schemaVersion());
                    List<BlockifyBlock> blocks = generateWithTimeout(generationRequest);
                    int beforeCandidate = chunks.size();
                    String fallbackReason = null;
                    for (BlockifyBlock block : blocks.stream().limit(properties.getMaxBlocksPerSection()).toList()) {
                        block = normalizeIdeaBlock(block, candidate);
                        String validationFailure = validationFailureReason(document, block, candidate);
                        if (validationFailure != null) {
                            fallbackReason = fallbackReason == null ? validationFailure : fallbackReason;
                            continue;
                        }
                        Chunk chunk = toBlockifyChunk(document, context, candidate, block, order++);
                        chunks.add(chunk);
                    }
                    if (chunks.size() == beforeCandidate) {
                        order = appendFallback(document, context, candidate, chunks, order,
                                fallbackReason == null ? "NO_VALID_BLOCKIFY_BLOCK" : fallbackReason);
                    }
                } catch (RuntimeException ex) {
                    if (ex instanceof BlockifyPiiMaskingException piiEx && piiEx.failPipeline()) {
                        throw piiEx;
                    }
                    String reason = generationFailureReason(ex);
                    if ("LLM_GENERATION_TIMEOUT".equals(reason)) {
                        externalGenerationUnavailable = true;
                    }
                    int recoveredOrder = appendRecoveryBlockify(document, context, candidate, chunks, order, reason);
                    order = recoveredOrder == order ? appendFallback(document, context, candidate, chunks, order, reason)
                            : recoveredOrder;
                }
            }
            if (coverageRatio(chunks, section, coveredBefore) < 0.95d) {
                order = appendMissingCoverageFallbacks(document, context, section, chunks, order);
            }
        }
        return linkNeighbors(withChunkingSummary(distill(chunks)));
    }

    private List<NormalizedBlock> blocksFromMarkdownText(String sourceDocumentId, String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<NormalizedBlock> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int order = 0;
        int blockIndex = 0;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.stripTrailing();
            if (line.isBlank()) {
                if (!paragraph.isEmpty()) {
                    BlockCursor cursor = appendMarkdownParagraphBlocks(
                            blocks, sourceDocumentId, blockIndex, order, paragraph.toString().trim(), metadata);
                    blockIndex = cursor.blockIndex();
                    order = cursor.order();
                    paragraph.setLength(0);
                }
                continue;
            }
            Matcher article = ARTICLE_HEADING.matcher(line);
            if (article.matches()) {
                if (!paragraph.isEmpty()) {
                    BlockCursor cursor = appendMarkdownParagraphBlocks(
                            blocks, sourceDocumentId, blockIndex, order, paragraph.toString().trim(), metadata);
                    blockIndex = cursor.blockIndex();
                    order = cursor.order();
                    paragraph.setLength(0);
                }
                blocks.add(markdownBlock(sourceDocumentId, blockIndex++, order++, NormalizedBlockType.HEADING,
                        cleanHeadingLine(article.group(1)), metadata));
                String body = cleanHeadingLine(article.group(2));
                if (!body.isBlank()) {
                    paragraph.append(body);
                }
                continue;
            }
            if (isMarkdownHeadingLine(line)) {
                if (!paragraph.isEmpty()) {
                    BlockCursor cursor = appendMarkdownParagraphBlocks(
                            blocks, sourceDocumentId, blockIndex, order, paragraph.toString().trim(), metadata);
                    blockIndex = cursor.blockIndex();
                    order = cursor.order();
                    paragraph.setLength(0);
                }
                blocks.add(markdownBlock(sourceDocumentId, blockIndex++, order++, NormalizedBlockType.HEADING,
                        cleanHeadingLine(line), metadata));
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }
        if (!paragraph.isEmpty()) {
            appendMarkdownParagraphBlocks(blocks, sourceDocumentId, blockIndex, order,
                    paragraph.toString().trim(), metadata);
        }
        if (blocks.isEmpty()) {
            return List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, text)
                    .id(sectionId(sourceDocumentId, 0))
                    .order(0)
                    .metadata(metadata)
                    .build());
        }
        return blocks;
    }

    private BlockCursor appendMarkdownParagraphBlocks(
            List<NormalizedBlock> blocks,
            String sourceDocumentId,
            int blockIndex,
            int order,
            String text,
            Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return new BlockCursor(blockIndex, order);
        }
        List<String> parts = shouldSplitText(text) ? splitTextByTokenBudget(text) : List.of(text);
        if (parts.isEmpty()) {
            parts = List.of(text);
        }
        int limit = Math.min(parts.size(), MAX_SPLIT_SOURCE_BLOCKS);
        String parentId = sectionId(sourceDocumentId, blockIndex);
        for (int partIndex = 0; partIndex < limit; partIndex++) {
            Map<String, Object> partMetadata = metadata;
            if (limit > 1) {
                partMetadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
                partMetadata.put(KEY_SOURCE_BLOCK_SPLIT, true);
                partMetadata.put(KEY_SOURCE_BLOCK_PARENT_ID, parentId);
                partMetadata.put(KEY_SOURCE_BLOCK_PART_INDEX, partIndex);
                partMetadata.put(KEY_SOURCE_BLOCK_PART_COUNT, limit);
            }
            blocks.add(markdownBlock(sourceDocumentId, blockIndex++, order++, NormalizedBlockType.PARAGRAPH,
                    parts.get(partIndex), partMetadata));
        }
        return new BlockCursor(blockIndex, order);
    }

    private NormalizedBlock markdownBlock(
            String sourceDocumentId,
            int blockIndex,
            int order,
            NormalizedBlockType type,
            String text,
            Map<String, Object> metadata) {
        return NormalizedBlock.builder(type, text)
                .id(sectionId(sourceDocumentId, blockIndex))
                .order(order)
                .metadata(metadata)
                .build();
    }

    private String validationFailureReason(NormalizedDocument document, BlockifyBlock block, Section section) {
        if (block == null || isBlank(block.question()) || isBlank(block.answer())) {
            return REASON_ANSWER_HAS_NO_BODY;
        }
        String answer = normalize(stripMarkdownHeading(block.answer()));
        String title = normalize(stripMarkdownHeading(block.title()));
        String heading = normalize(stripMarkdownHeading(section.headingPath()));
        if (answer.equals(title) || answer.equals(heading)) {
            return "ANSWER_EQUALS_TITLE";
        }
        if (isHeadingOnlyText(block.answer())) {
            return REASON_HEADING_ONLY;
        }
        if (isGenericQuestion(block.question())) {
            return REASON_GENERIC_QUESTION;
        }
        if (!properties.isRequireSourceEvidence()) {
            return null;
        }
        if (block.sourceEvidence() == null || block.sourceEvidence().isEmpty()) {
            return REASON_EVIDENCE_NOT_FOUND;
        }
        String sourceText = content(section.blocks()).replaceAll("\\s+", " ");
        boolean evidenceFound = block.sourceEvidence().stream()
                .map(BlockifySourceEvidence::text)
                .filter(text -> text != null && !text.isBlank())
                .filter(text -> text.trim().length() >= properties.getMinEvidenceChars())
                .map(text -> text.replaceAll("\\s+", " "))
                .anyMatch(sourceText::contains);
        if (!evidenceFound) {
            return REASON_EVIDENCE_NOT_FOUND;
        }
        if (!trustedFactsAreGrounded(block, sourceText)) {
            return "TRUSTED_ANSWER_FACT_MISMATCH";
        }
        String typedFailure = typedFieldValidator.validate(effectiveDocumentType(document), block, sourceText);
        if (typedFailure != null) {
            return typedFailure;
        }
        if (answer.length() < properties.getMinAnswerChars()) {
            return REASON_ANSWER_TOO_SHORT;
        }
        String chunkContent = blockContent(block);
        if (chunkContent.length() < properties.getMinChunkChars()) {
            return REASON_ANSWER_TOO_SHORT;
        }
        if (chunkContent.length() > properties.getMaxChunkChars()) {
            return "CHUNK_TOO_LONG";
        }
        return null;
    }

    private boolean trustedFactsAreGrounded(BlockifyBlock block, String sourceText) {
        String answer = block.answer() == null ? "" : block.answer();
        Matcher matcher = FACT_TOKEN.matcher(answer);
        while (matcher.find()) {
            String token = matcher.group().replaceAll("\\s+", "");
            if (token.isBlank()) {
                continue;
            }
            String normalizedSource = sourceText == null ? "" : sourceText.replaceAll("\\s+", "");
            if (!normalizedSource.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private List<Section> sourceBlockCandidates(NormalizedDocument document, Section section) {
        List<NormalizedBlock> headings = section.blocks().stream()
                .filter(this::isHeading)
                .toList();
        List<NormalizedBlock> bodyBlocks = section.blocks().stream()
                .filter(block -> !isHeading(block))
                .filter(block -> block.text() != null && !block.text().isBlank())
                .toList();
        if (bodyBlocks.isEmpty()) {
            return List.of();
        }
        if (bodyBlocks.size() == 1) {
            NormalizedBlock body = bodyBlocks.get(0);
            BlockifyDocumentType documentType = effectiveDocumentType(document);
            if (documentType == BlockifyDocumentType.POLICY && shouldSplitPolicyBodyBlock(body)) {
                return splitProfileBodyBlock(section, headings, body, splitPolicyText(body.text()), "policy");
            }
            if (documentType == BlockifyDocumentType.NARRATIVE && shouldSplitNarrativeBodyBlock(body)) {
                return splitProfileBodyBlock(section, headings, body, splitNarrativeText(body.text()), "narrative");
            }
            if (shouldSplitSingleBodyBlock(body)) {
                return splitOversizedBodyBlock(section, headings, body);
            }
            return List.of(section);
        }
        List<Section> candidates = new ArrayList<>();
        for (NormalizedBlock body : bodyBlocks) {
            List<NormalizedBlock> blocks = new ArrayList<>(headings);
            blocks.add(body);
            int order = body.order() == null ? candidates.size() : body.order();
            candidates.add(new Section(
                    section.sectionId() + "-block-" + order,
                    section.headingPath(),
                    order,
                    order,
                    List.copyOf(blocks)));
        }
        return candidates;
    }

    private List<Section> splitOversizedBodyBlock(
            Section section,
            List<NormalizedBlock> headings,
            NormalizedBlock body) {
        return splitProfileBodyBlock(section, headings, body, splitTextByTokenBudget(body.text()), "part");
    }

    private List<Section> splitProfileBodyBlock(
            Section section,
            List<NormalizedBlock> headings,
            NormalizedBlock body,
            List<String> parts,
            String reason) {
        if (parts.isEmpty()) {
            return List.of(section);
        }
        List<Section> candidates = new ArrayList<>();
        int baseOrder = body.order() == null ? section.startOrder() : body.order();
        int limit = Math.min(parts.size(), MAX_SPLIT_SOURCE_BLOCKS);
        for (int i = 0; i < limit; i++) {
            NormalizedBlock split = NormalizedBlock.builder(body.type(), parts.get(i))
                    .id(firstNonBlank(body.id(), section.sectionId()) + "-part-" + i)
                    .sourceRef(firstNonBlank(body.sourceRef(), body.effectiveSourceRef()) + "#part-" + i)
                    .page(body.page())
                    .slide(body.slide())
                    .order(baseOrder + i)
                    .parentBlockId(firstNonBlank(body.parentBlockId(), body.id()))
                    .headingPath(body.headingPath())
                    .blockIds(body.blockIds())
                    .confidence(body.confidence())
                    .metadata(splitMetadata(body.metadata(), reason, i, limit))
                    .build();
            List<NormalizedBlock> blocks = new ArrayList<>(headings);
            blocks.add(split);
            candidates.add(new Section(
                    section.sectionId() + "-block-" + baseOrder + "-part-" + i,
                    section.headingPath(),
                    baseOrder + i,
                    baseOrder + i,
                    List.copyOf(blocks)));
        }
        return candidates;
    }

    private Map<String, Object> splitMetadata(Map<String, Object> metadata, String reason, int partIndex, int partCount) {
        Map<String, Object> values = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        values.put(KEY_SOURCE_BLOCK_SPLIT, true);
        values.put("sourceBlockSplitReason", reason);
        values.put(KEY_SOURCE_BLOCK_PART_INDEX, partIndex);
        values.put(KEY_SOURCE_BLOCK_PART_COUNT, partCount);
        return values;
    }

    private boolean shouldSplitPolicyBodyBlock(NormalizedBlock body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            return false;
        }
        return splitPolicyText(body.text()).size() > 1;
    }

    private boolean shouldSplitNarrativeBodyBlock(NormalizedBlock body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            return false;
        }
        return body.text().length() > Math.max(420, properties.getMaxChunkChars() / 2)
                && splitNarrativeText(body.text()).size() > 1;
    }

    private List<String> splitPolicyText(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            return List.of(text == null ? "" : text.trim()).stream()
                    .filter(value -> !value.isBlank())
                    .toList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            boolean startsException = sentence.startsWith("다만") || sentence.contains(" 예외")
                    || sentence.contains(" 제외");
            if (!current.isEmpty() && (startsException || current.length() + sentence.length() > 520)) {
                parts.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.stream().filter(value -> value.length() >= 20).toList();
    }

    private List<String> splitNarrativeText(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            return splitTextByTokenBudget(text);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int sentenceCount = 0;
        for (String sentence : sentences) {
            boolean dialogue = sentence.contains("\"") || sentence.contains("“") || sentence.contains("”")
                    || sentence.toLowerCase(Locale.ROOT).contains(" said")
                    || sentence.toLowerCase(Locale.ROOT).contains(" asked");
            if (!current.isEmpty()
                    && (sentenceCount >= 2 || current.length() + sentence.length() > 700 || dialogue)) {
                parts.add(current.toString().trim());
                current.setLength(0);
                sentenceCount = 0;
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
            sentenceCount++;
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.stream().filter(value -> value.length() >= 40).toList();
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace('\r', '\n').replaceAll("\\s+", " ").trim();
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile(".+?(?:[.!?。！？]|다\\.|요\\.|$)(?:\\s+|$)").matcher(normalized);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isBlank()) {
                values.add(sentence);
            }
        }
        return values.isEmpty() ? List.of(normalized) : values;
    }

    private boolean shouldSplitSingleBodyBlock(NormalizedBlock body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            return false;
        }
        return shouldSplitText(body.text());
    }

    private boolean shouldSplitText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return ChunkSizing.estimateTokens(text) > properties.getMaxInputTokens()
                || text.length() > properties.getMaxChunkChars() * LONG_SOURCE_BLOCK_SPLIT_FACTOR;
    }

    private List<String> splitTextByTokenBudget(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int maxChars = Math.max(240, Math.min(properties.getMaxInputTokens() * 3,
                properties.getMaxChunkChars() * LONG_SOURCE_BLOCK_SPLIT_FACTOR));
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.trim().split("(?<=[.!?。！？])\\s+")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + sentence.length() + 1 > maxChars) {
                parts.add(current.toString().trim());
                current.setLength(0);
            }
            if (sentence.length() > maxChars) {
                flushWindowed(sentence, maxChars, parts);
                continue;
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private void flushWindowed(String text, int maxChars, List<String> parts) {
        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(text.length(), offset + maxChars);
            parts.add(text.substring(offset, end).trim());
            offset = end;
        }
    }

    private BlockifyBlock normalizeIdeaBlock(BlockifyBlock block, Section section) {
        if (block == null) {
            return null;
        }
        BlockifyBlock.SourceBlockRange range = block.sourceBlockRange();
        if (range == null) {
            range = new BlockifyBlock.SourceBlockRange(section.startOrder(), section.endOrder());
        }
        String sourceSectionId = firstNonBlank(block.sourceSectionId(), section.sectionId());
        String name = firstNonBlank(block.name(), block.title(), section.headingPath());
        String entityName = firstNonBlank(block.entityName(), section.headingPath(), name);
        String entityType = firstNonBlank(block.entityType(), inferEntityType(section));
        return new BlockifyBlock(
                name,
                block.title(),
                block.criticalQuestion(),
                block.trustedAnswer(),
                block.keywords(),
                block.tags(),
                entityName,
                entityType,
                block.sourceEvidence(),
                range,
                sourceSectionId,
                block.confidence(),
                block.typedFields());
    }

    private String inferEntityType(Section section) {
        String heading = section.headingPath() == null ? "" : section.headingPath();
        if (heading.matches(".*제\\s*\\d+조.*")) {
            return "article";
        }
        if (containsTable(section.blocks())) {
            return "table";
        }
        return "section";
    }

    private double coverageRatio(List<Chunk> chunks, Section section, int coveredBefore) {
        int target = sourceBlockOrders(section).size();
        if (target == 0) {
            return 1.0d;
        }
        int covered = coveredSourceBlockCount(chunks, section) - coveredBefore;
        return Math.max(0, covered) / (double) target;
    }

    private int coveredSourceBlockCount(List<Chunk> chunks, Section section) {
        List<Integer> target = sourceBlockOrders(section);
        if (target.isEmpty()) {
            return 0;
        }
        return (int) target.stream()
                .filter(order -> chunks.stream().anyMatch(chunk -> coversSourceOrder(chunk, section, order)))
                .count();
    }

    private boolean coversSourceOrder(Chunk chunk, Section section, Integer order) {
        Map<String, Object> metadata = chunk.metadata().toMap();
        String sourceSectionId = Objects.toString(metadata.getOrDefault("sourceSectionId", ""), "");
        if (!sourceSectionId.startsWith(section.sectionId())) {
            return false;
        }
        Object range = metadata.get("sourceBlockRange");
        if (range instanceof Map<?, ?> map) {
            Integer start = integer(map.get("start"));
            Integer end = integer(map.get("end"));
            return start != null && end != null && order >= start && order <= end;
        }
        return chunk.metadata().blockIds().stream()
                .anyMatch(id -> id.equals(Integer.toString(order)) || id.endsWith("block-" + order));
    }

    private int appendMissingCoverageFallbacks(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            List<Chunk> chunks,
            int order) {
        for (NormalizedBlock block : section.blocks()) {
            if (isHeading(block) || block.text() == null || block.text().isBlank()) {
                continue;
            }
            Integer blockOrder = block.order();
            if (blockOrder != null && coversSourceOrderInChunks(chunks, section, blockOrder)) {
                continue;
            }
            Section missing = new Section(
                    section.sectionId() + "-missing-" + (blockOrder == null ? order : blockOrder),
                    section.headingPath(),
                    blockOrder == null ? order : blockOrder,
                    blockOrder == null ? order : blockOrder,
                    List.of(block));
            order = appendFallback(document, context, missing, chunks, order, "COVERAGE_GAP");
        }
        return order;
    }

    private boolean coversSourceOrderInChunks(List<Chunk> chunks, Section section, Integer order) {
        return chunks.stream().anyMatch(chunk -> coversSourceOrder(chunk, section, order));
    }

    private List<Integer> sourceBlockOrders(Section section) {
        return section.blocks().stream()
                .filter(block -> !isHeading(block))
                .filter(block -> block.text() != null && !block.text().isBlank())
                .map(NormalizedBlock::order)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Chunk> withChunkingSummary(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        ChunkingSummary summary = chunkingSummary(chunks);
        List<Chunk> enriched = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> attributes = new LinkedHashMap<>(chunk.metadata().attributes());
            attributes.put("ideaBlockChunkCount", summary.chunkCount());
            attributes.put("ideaBlockCount", summary.ideaBlockCount());
            attributes.put("ideaBlockFallbackCount", summary.fallbackCount());
            attributes.put("ideaBlockFallbackReasonCounts", summary.fallbackReasonCounts());
            attributes.put("ideaBlockSourceBlockTargetCount", summary.sourceBlockTargetCount());
            attributes.put("ideaBlockSourceBlockCoveredCount", summary.sourceBlockCoveredCount());
            attributes.put("ideaBlockSourceBlockCoverage", summary.sourceBlockCoverage());
            if (summary.averageConfidence() != null) {
                attributes.put("ideaBlockAverageConfidence", summary.averageConfidence());
            }
            enriched.add(withAttributes(chunk, attributes));
        }
        return enriched;
    }

    private List<Chunk> distill(List<Chunk> chunks) {
        if (!properties.isDistillationEnabled() || chunks.isEmpty()) {
            return chunks;
        }
        List<Chunk> distilled = new ArrayList<>(chunks.size());
        Map<String, Integer> groupSizes = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        int dropped = 0;
        for (Chunk chunk : chunks) {
            Map<String, Object> metadata = chunk.metadata().toMap();
            if (!isIdeaBlockChunk(metadata)) {
                distilled.add(chunk);
                continue;
            }
            String key = distillationKey(metadata);
            groupSizes.merge(key, 1, Integer::sum);
            if (!seen.add(key)) {
                dropped++;
                continue;
            }
            distilled.add(chunk);
        }
        Map<String, SimilarityCluster> clusters = similarityClusters(distilled);
        return withDistillationMetadata(distilled, dropped, groupSizes, clusters);
    }

    private List<Chunk> withDistillationMetadata(
            List<Chunk> chunks,
            int droppedDuplicateCount,
            Map<String, Integer> groupSizes,
            Map<String, SimilarityCluster> clusters) {
        List<Chunk> enriched = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> attributes = new LinkedHashMap<>(chunk.metadata().attributes());
            attributes.put("ideaBlockDistillationEnabled", true);
            attributes.put("ideaBlockDistillationStrategy", "normalized-question-answer-dedup+lexical-similarity-candidates");
            attributes.put("ideaBlockDistillationDroppedDuplicateCount", droppedDuplicateCount);
            if (isIdeaBlockChunk(chunk.metadata().toMap())) {
                String key = distillationKey(chunk.metadata().toMap());
                attributes.put("distilled", true);
                attributes.put("ideaBlockDistillationKey", key);
                attributes.put("ideaBlockDistillationGroupSize", groupSizes.getOrDefault(key, 1));
                SimilarityCluster cluster = clusters.get(chunk.id());
                if (cluster != null) {
                    attributes.put("ideaBlockSimilarityClusterId", cluster.clusterId());
                    attributes.put("ideaBlockSimilarityClusterSize", cluster.memberCount());
                    attributes.put("ideaBlockSimilarityMaxScore", cluster.maxScore());
                    attributes.put("ideaBlockMergeCandidate", cluster.memberCount() > 1);
                    attributes.put("ideaBlockMergePolicy", "candidate-only");
                }
            }
            enriched.add(withAttributes(chunk, attributes));
        }
        return enriched;
    }

    private Map<String, SimilarityCluster> similarityClusters(List<Chunk> chunks) {
        List<Chunk> ideaBlocks = chunks.stream()
                .filter(chunk -> isIdeaBlockChunk(chunk.metadata().toMap()))
                .toList();
        if (ideaBlocks.size() < 2) {
            return Map.of();
        }
        DisjointSet groups = new DisjointSet(ideaBlocks.size());
        double[][] scores = new double[ideaBlocks.size()][ideaBlocks.size()];
        for (int left = 0; left < ideaBlocks.size(); left++) {
            for (int right = left + 1; right < ideaBlocks.size(); right++) {
                Map<String, Object> leftMetadata = ideaBlocks.get(left).metadata().toMap();
                Map<String, Object> rightMetadata = ideaBlocks.get(right).metadata().toMap();
                if (!mergeSafe(leftMetadata, rightMetadata)) {
                    continue;
                }
                double score = lexicalSimilarity(leftMetadata, rightMetadata);
                scores[left][right] = score;
                scores[right][left] = score;
                if (score >= properties.getDistillationSimilarityThreshold()) {
                    groups.union(left, right);
                }
            }
        }
        Map<Integer, List<Integer>> membersByRoot = new LinkedHashMap<>();
        for (int index = 0; index < ideaBlocks.size(); index++) {
            membersByRoot.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>()).add(index);
        }
        Map<String, SimilarityCluster> result = new LinkedHashMap<>();
        int clusterNo = 1;
        for (List<Integer> members : membersByRoot.values()) {
            if (members.size() < 2) {
                continue;
            }
            double maxScore = 0.0d;
            for (int left : members) {
                for (int right : members) {
                    maxScore = Math.max(maxScore, scores[left][right]);
                }
            }
            String clusterId = "sim-" + clusterNo++;
            SimilarityCluster cluster = new SimilarityCluster(clusterId, members.size(), maxScore);
            for (int member : members) {
                result.put(ideaBlocks.get(member).id(), cluster);
            }
        }
        return result;
    }

    private boolean mergeSafe(Map<String, Object> left, Map<String, Object> right) {
        Set<String> leftFacts = factTokens(left);
        Set<String> rightFacts = factTokens(right);
        if (!leftFacts.equals(rightFacts)) {
            return false;
        }
        if (!compatibleScalar(left, right, "entityType")) {
            return false;
        }
        return compatibleEntityName(left, right);
    }

    private boolean compatibleScalar(Map<String, Object> left, Map<String, Object> right, String key) {
        String leftValue = normalize(text(left.get(key)));
        String rightValue = normalize(text(right.get(key)));
        return leftValue.isBlank() || rightValue.isBlank() || leftValue.equals(rightValue);
    }

    private boolean compatibleEntityName(Map<String, Object> left, Map<String, Object> right) {
        String leftValue = normalize(text(left.get("entityName")));
        String rightValue = normalize(text(right.get("entityName")));
        return leftValue.isBlank() || rightValue.isBlank()
                || leftValue.equals(rightValue)
                || leftValue.contains(rightValue)
                || rightValue.contains(leftValue);
    }

    private Set<String> factTokens(Map<String, Object> metadata) {
        String text = firstNonBlank(text(metadata.get("answer")), text(metadata.get("trustedAnswer")));
        if (text == null) {
            return Set.of();
        }
        Matcher matcher = FACT_TOKEN.matcher(text);
        Set<String> tokens = new TreeSet<>();
        while (matcher.find()) {
            String token = matcher.group().replaceAll("\\s+", "");
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double lexicalSimilarity(Map<String, Object> left, Map<String, Object> right) {
        Set<String> leftTerms = similarityTerms(left);
        Set<String> rightTerms = similarityTerms(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0.0d;
        }
        Set<String> intersection = new LinkedHashSet<>(leftTerms);
        intersection.retainAll(rightTerms);
        Set<String> union = new LinkedHashSet<>(leftTerms);
        union.addAll(rightTerms);
        return union.isEmpty() ? 0.0d : intersection.size() / (double) union.size();
    }

    private Set<String> similarityTerms(Map<String, Object> metadata) {
        String value = String.join(" ",
                firstNonBlank(text(metadata.get("question")), ""),
                firstNonBlank(text(metadata.get("answer")), ""),
                firstNonBlank(text(metadata.get("entityName")), ""),
                firstNonBlank(text(metadata.get("entityType")), ""));
        Set<String> terms = new LinkedHashSet<>();
        for (String term : normalize(value).split("[^0-9a-z가-힣]+")) {
            if (term.length() >= 2) {
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean isIdeaBlockChunk(Map<String, Object> metadata) {
        return "ideaBlock".equals(text(metadata.get("chunkType")))
                || ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")));
    }

    private String distillationKey(Map<String, Object> metadata) {
        String sourceRange = sourceRangeKey(metadata);
        String question = normalize(text(metadata.get("question")));
        String answer = normalize(text(metadata.get("answer")));
        if (!question.isBlank() || !answer.isBlank()) {
            return "range:" + sourceRange + "\nqa:" + question + "\n" + answer;
        }
        String fingerprint = firstNonBlank(text(metadata.get("ideaBlockFingerprint")),
                text(metadata.get("blockifyFingerprint")), text(metadata.get("fingerprint")));
        if (fingerprint != null) {
            return "range:" + sourceRange + "\n" + fingerprint;
        }
        return "range:" + sourceRange + "\nchunk:" + metadata.hashCode();
    }

    private String sourceRangeKey(Map<String, Object> metadata) {
        Object range = metadata == null ? null : metadata.get("sourceBlockRange");
        if (range instanceof Map<?, ?> map) {
            return Objects.toString(map.get("start"), "") + ":" + Objects.toString(map.get("end"), "");
        }
        return "unknown";
    }

    private ChunkingSummary chunkingSummary(List<Chunk> chunks) {
        int ideaBlockCount = 0;
        int fallbackCount = 0;
        Map<String, Integer> fallbackReasonCounts = new LinkedHashMap<>();
        Set<Integer> sourceBlockTargets = new LinkedHashSet<>();
        Set<Integer> sourceBlockCovered = new LinkedHashSet<>();
        double confidenceSum = 0.0d;
        int confidenceCount = 0;
        for (Chunk chunk : chunks) {
            Map<String, Object> metadata = chunk.metadata().toMap();
            boolean ideaBlock = "ideaBlock".equals(text(metadata.get("chunkType")))
                    || ChunkingStrategyType.BLOCKIFY.value().equals(text(metadata.get("actualChunkingStrategy")));
            boolean fallback = VALIDATION_FALLBACK.equals(text(metadata.get("validationStatus")))
                    || ChunkingStrategyType.STRUCTURE_BASED.value().equals(text(metadata.get("actualChunkingStrategy")));
            if (ideaBlock) {
                ideaBlockCount++;
            }
            if (fallback) {
                fallbackCount++;
                String reason = firstNonBlank(text(metadata.get("fallbackReason")), "UNKNOWN_FALLBACK");
                fallbackReasonCounts.merge(reason, 1, Integer::sum);
            }
            addRange(sourceBlockTargets, metadata.get("sourceBlockRange"));
            if (ideaBlock || fallback) {
                addRange(sourceBlockCovered, metadata.get("sourceBlockRange"));
            }
            Double confidence = doubleValue(metadata.get("confidence"));
            if (confidence != null) {
                confidenceSum += confidence;
                confidenceCount++;
            }
        }
        int targetCount = sourceBlockTargets.size();
        int coveredCount = sourceBlockCovered.size();
        double coverage = targetCount == 0 ? 0.0d : coveredCount / (double) targetCount;
        Double averageConfidence = confidenceCount == 0 ? null : confidenceSum / confidenceCount;
        return new ChunkingSummary(chunks.size(), ideaBlockCount, fallbackCount, Map.copyOf(fallbackReasonCounts),
                targetCount, coveredCount, coverage, averageConfidence);
    }

    private void addRange(Set<Integer> values, Object range) {
        if (!(range instanceof Map<?, ?> map)) {
            return;
        }
        Integer start = integer(map.get("start"));
        Integer end = integer(map.get("end"));
        if (start == null || end == null) {
            return;
        }
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        for (int value = from; value <= to; value++) {
            values.add(value);
        }
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Chunk withAttributes(Chunk chunk, Map<String, Object> attributes) {
        ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                .sourceDocumentId(chunk.metadata().sourceDocumentId())
                .parentId(chunk.metadata().parentId())
                .chunkType(chunk.metadata().chunkType())
                .parentChunkId(chunk.metadata().parentChunkId())
                .previousChunkId(chunk.metadata().previousChunkId())
                .nextChunkId(chunk.metadata().nextChunkId())
                .section(chunk.metadata().section())
                .objectType(chunk.metadata().objectType())
                .objectId(chunk.metadata().objectId())
                .startOffset(chunk.metadata().startOffset())
                .endOffset(chunk.metadata().endOffset())
                .tokenCount(chunk.metadata().tokenCount())
                .charCount(chunk.metadata().charCount())
                .blockIds(chunk.metadata().blockIds())
                .confidence(chunk.metadata().confidence())
                .attributes(attributes)
                .build();
        return Chunk.of(chunk.id(), chunk.content(), metadata);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Chunk toBlockifyChunk(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            BlockifyBlock block,
            int order) {
        String content = blockContent(block);
        Map<String, Object> attributes = metadata(document, section, block, ChunkingStrategyType.BLOCKIFY,
                VALIDATION_RULE_VALIDATED, null);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .chunkType(ChunkType.IDEA_BLOCK)
                .section(section.headingPath())
                .objectType(context.objectType())
                .objectId(context.objectId())
                .charCount(content.length())
                .tokenCount(ChunkSizing.estimateTokens(content))
                .blockIds(blockIds(section.blocks()))
                .confidence(block.confidence())
                .attributes(attributes)
                .build();
        return Chunk.of(chunkId(effectiveSourceDocumentId(document, context), order), content, metadata);
    }

    private int appendRecoveryBlockify(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            List<Chunk> chunks,
            int order,
            String primaryFailureReason) {
        List<BlockifyBlock> blocks;
        try {
            blocks = recoveryGenerator.generate(new BlockifyGenerationRequest(
                    effectiveSourceDocumentId(document, context),
                    section.sectionId(),
                    section.headingPath(),
                    section.blocks(),
                    properties.getPromptVersion(),
                    null,
                    null,
                    GENERATOR_MODEL_HEURISTIC,
                    0.0d,
                    1.0d,
                    false,
                    Math.min(1, properties.getMaxBlocksPerSection()),
                    effectiveDocumentType(document),
                    metadataValue(document, KEY_BLOCKIFY_PROFILE),
                    metadataValue(document, KEY_IDEA_BLOCK_SCHEMA_VERSION)));
        } catch (RuntimeException ignored) {
            return order;
        }
        for (BlockifyBlock block : blocks.stream().limit(1).toList()) {
            block = compactRecoveryBlock(block, section);
            String validationFailure = validationFailureReason(document, block, section);
            if (validationFailure != null) {
                continue;
            }
            chunks.add(withRecoveryMetadata(toBlockifyChunk(document, context, section, block, order++),
                    primaryFailureReason));
        }
        return order;
    }

    private BlockifyBlock compactRecoveryBlock(BlockifyBlock block, Section section) {
        if (block == null) {
            return null;
        }
        String answer = compactText(block.answer(), 320);
        List<BlockifySourceEvidence> evidence = block.sourceEvidence() == null ? List.of() : block.sourceEvidence().stream()
                .map(value -> new BlockifySourceEvidence(
                        compactEvidence(value.text(), section),
                        value.normalizedBlockIndex(),
                        value.startOffset(),
                        value.endOffset(),
                        value.page(),
                        value.slide(),
                        value.headingPath(),
                        value.sourceSectionId(),
                        value.sourceBlockIndexes()))
                .filter(value -> value.text() != null && value.text().trim().length() >= properties.getMinEvidenceChars())
                .limit(1)
                .toList();
        return new BlockifyBlock(
                block.name(),
                block.title(),
                block.criticalQuestion(),
                answer,
                block.keywords(),
                block.tags(),
                block.entityName(),
                block.entityType(),
                evidence,
                block.sourceBlockRange(),
                block.sourceSectionId(),
                block.confidence(),
                block.typedFields());
    }

    private String compactEvidence(String evidence, Section section) {
        String compact = compactText(evidence, 180);
        if (compact.length() >= properties.getMinEvidenceChars()) {
            return compact;
        }
        return compactText(content(section.blocks()), 180);
    }

    private String compactText(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        int limit = Math.max(properties.getMinEvidenceChars(), maxLength);
        int end = Math.min(normalized.length(), limit);
        for (String marker : List.of("다. ", ". ", "? ", "! ")) {
            int candidate = normalized.lastIndexOf(marker, end);
            if (candidate >= properties.getMinEvidenceChars()) {
                end = candidate + marker.trim().length();
                break;
            }
        }
        return normalized.substring(0, end).trim();
    }

    private Chunk withRecoveryMetadata(Chunk chunk, String primaryFailureReason) {
        Map<String, Object> attributes = new LinkedHashMap<>(chunk.metadata().attributes());
        attributes.put("generationMode", GENERATION_MODE_RECOVERY_HEURISTIC);
        attributes.put("primaryFailureReason", primaryFailureReason);
        attributes.put("generatorType", "heuristic");
        attributes.put("generatorModel", GENERATOR_MODEL_HEURISTIC);
        attributes.remove("generatorProvider");
        attributes.remove("generatorLlmModel");
        ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                .sourceDocumentId(chunk.metadata().sourceDocumentId())
                .parentId(chunk.metadata().parentId())
                .chunkType(chunk.metadata().chunkType())
                .parentChunkId(chunk.metadata().parentChunkId())
                .previousChunkId(chunk.metadata().previousChunkId())
                .nextChunkId(chunk.metadata().nextChunkId())
                .section(chunk.metadata().section())
                .objectType(chunk.metadata().objectType())
                .objectId(chunk.metadata().objectId())
                .startOffset(chunk.metadata().startOffset())
                .endOffset(chunk.metadata().endOffset())
                .tokenCount(chunk.metadata().tokenCount())
                .charCount(chunk.metadata().charCount())
                .blockIds(chunk.metadata().blockIds())
                .confidence(chunk.metadata().confidence())
                .attributes(attributes)
                .build();
        return Chunk.of(chunk.id(), chunk.content(), metadata);
    }

    private Chunk withLocalHeuristicMetadata(Chunk chunk) {
        Map<String, Object> attributes = new LinkedHashMap<>(chunk.metadata().attributes());
        attributes.put("generationMode", GENERATION_MODE_LOCAL_HEURISTIC);
        attributes.put("primaryFailureReason", REASON_LLM_CANDIDATE_LIMIT);
        attributes.put("generatorType", "heuristic");
        attributes.put("generatorModel", GENERATOR_MODEL_HEURISTIC);
        attributes.put("externalGeneratorSkipped", true);
        attributes.remove("generatorProvider");
        attributes.remove("generatorLlmModel");
        return withAttributes(chunk, attributes);
    }

    private int appendFallback(
            NormalizedDocument document,
            ChunkingContext context,
            Section section,
            List<Chunk> chunks,
            int order,
            String reason) {
        NormalizedDocument sectionDocument = NormalizedDocument.builder(effectiveSourceDocumentId(document, context))
                .plainText(content(section.blocks()))
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(section.blocks())
                .metadata(document.metadata())
                .build();
        ChunkingContext fallbackContext = ChunkingContext.builder(sectionDocument.chunkableText())
                .sourceDocumentId(effectiveSourceDocumentId(document, context))
                .contentType(context.contentType())
                .filename(context.filename())
                .objectType(context.objectType())
                .objectId(context.objectId())
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(context.maxSize())
                .overlap(context.overlap())
                .unit(context.unit() == null ? ChunkUnit.CHARACTER : context.unit())
                .metadata(context.metadata())
                .build();
        for (Chunk fallback : fallbackChunker.chunk(sectionDocument, fallbackContext)) {
            Map<String, Object> attributes = new LinkedHashMap<>(fallback.metadata().attributes());
            attributes.putAll(metadata(document, section, null, ChunkingStrategyType.STRUCTURE_BASED,
                    VALIDATION_FALLBACK, reason));
            ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.STRUCTURE_BASED, order)
                    .sourceDocumentId(fallback.metadata().sourceDocumentId())
                    .chunkType(fallback.metadata().chunkType())
                    .parentChunkId(fallback.metadata().parentChunkId())
                    .section(fallback.metadata().section())
                    .objectType(fallback.metadata().objectType())
                    .objectId(fallback.metadata().objectId())
                    .startOffset(fallback.metadata().startOffset())
                    .endOffset(fallback.metadata().endOffset())
                    .tokenCount(fallback.metadata().tokenCount())
                    .charCount(fallback.metadata().charCount())
                    .blockIds(fallback.metadata().blockIds())
                    .confidence(fallback.metadata().confidence())
                    .attributes(attributes)
                    .build();
            chunks.add(Chunk.of(chunkId(effectiveSourceDocumentId(document, context), order++),
                    fallback.content(), metadata));
        }
        return order;
    }

    private Map<String, Object> metadata(
            NormalizedDocument document,
            Section section,
            BlockifyBlock block,
            ChunkingStrategyType actualStrategy,
            String validationStatus,
            String fallbackReason) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put("schemaVersion", SCHEMA_VERSION);
        metadata.put("requestedChunkingStrategy", ChunkingStrategyType.BLOCKIFY.value());
        metadata.put("actualChunkingStrategy", actualStrategy.value());
        metadata.put("validationStatus", validationStatus);
        metadata.put("promptVersion", properties.getPromptVersion());
        put(metadata, "generatorProvider", effectiveLlmProvider(document, null));
        put(metadata, "generatorLlmModel", effectiveLlmModel(document, null));
        metadata.put("generatorModel", effectiveGeneratorModel(document, null));
        put(metadata, KEY_BLOCKIFY_PII_MASKING_ENABLED, metadataValue(document, KEY_BLOCKIFY_PII_MASKING_ENABLED));
        metadata.put("temperature", properties.getTemperature());
        metadata.put("topP", properties.getTopP());
        put(metadata, "sourceSectionId", section.sectionId());
        put(metadata, "sourceBlockRange", sourceBlockRange(block, section));
        put(metadata, ChunkMetadata.KEY_HEADING_PATH, section.headingPath());
        if (fallbackReason != null) {
            metadata.put("fallbackReason", fallbackReason);
        }
        if (block != null) {
            put(metadata, "ideaBlockName", block.name());
            put(metadata, "title", block.title());
            put(metadata, "criticalQuestion", block.criticalQuestion());
            put(metadata, "trustedAnswer", block.trustedAnswer());
            put(metadata, "question", block.question());
            put(metadata, "answer", block.answer());
            put(metadata, "keywords", block.keywords());
            put(metadata, "tags", block.tags());
            put(metadata, "entityName", block.entityName());
            put(metadata, "entityType", block.entityType());
            put(metadata, "sourceEvidence", block.sourceEvidence());
            putTypedFields(metadata, block.typedFields());
            put(metadata, "sourceSectionId", firstNonBlank(block.sourceSectionId(), section.sectionId()));
            String fingerprint = fingerprint(document, section, block);
            metadata.put("fingerprint", fingerprint);
            metadata.put("ideaBlockFingerprint", fingerprint);
            metadata.put("blockifyFingerprint", fingerprint);
        }
        return metadata;
    }

    private Map<String, Object> sourceBlockRange(BlockifyBlock block, Section section) {
        BlockifyBlock.SourceBlockRange range = block == null ? null : block.sourceBlockRange();
        int start = range == null || range.start() == null ? section.startOrder() : range.start();
        int end = range == null || range.end() == null ? section.endOrder() : range.end();
        return Map.of("start", start, "end", end);
    }

    private String fingerprint(NormalizedDocument document, Section section, BlockifyBlock block) {
        String raw = String.join("\n",
                document.sourceDocumentId(),
                firstNonBlank(block.sourceSectionId(), section.sectionId()),
                String.valueOf(sourceBlockRange(block, section).get("start")),
                String.valueOf(sourceBlockRange(block, section).get("end")),
                normalize(block.name()),
                normalize(block.question()),
                normalize(block.answer()),
                effectiveGeneratorModel(document, null),
                properties.getPromptVersion());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private List<Section> splitSections(NormalizedDocument document) {
        List<NormalizedBlock> blocks = document.blocks().isEmpty()
                ? List.of(NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, document.chunkableText())
                        .id(sectionId(document.sourceDocumentId(), 0))
                        .order(0)
                        .build())
                : document.blocks();
        List<Section> sections = new ArrayList<>();
        List<NormalizedBlock> current = new ArrayList<>();
        String headingPath = "";
        int startOrder = 0;
        for (NormalizedBlock block : blocks) {
            if (isHeading(block)) {
                if (!current.isEmpty()) {
                    sections.add(section(document, sections.size(), headingPath, startOrder, current));
                }
                current = new ArrayList<>();
                headingPath = resolveHeadingPath(block);
                startOrder = block.order() == null ? sections.size() : block.order();
                current.add(block);
                continue;
            }
            if (current.isEmpty()) {
                startOrder = block.order() == null ? sections.size() : block.order();
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            sections.add(section(document, sections.size(), headingPath, startOrder, current));
        }
        return sections;
    }

    private Section section(
            NormalizedDocument document,
            int index,
            String headingPath,
            int startOrder,
            List<NormalizedBlock> blocks) {
        int endOrder = blocks.stream()
                .map(NormalizedBlock::order)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(startOrder);
        return new Section(sectionId(document.sourceDocumentId(), index), headingPath, startOrder, endOrder, List.copyOf(blocks));
    }

    private List<Chunk> linkNeighbors(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<Chunk> linked = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            Chunk previous = index == 0 ? null : chunks.get(index - 1);
            Chunk next = index == chunks.size() - 1 ? null : chunks.get(index + 1);
            ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                    .sourceDocumentId(chunk.metadata().sourceDocumentId())
                    .parentId(chunk.metadata().parentId())
                    .chunkType(chunk.metadata().chunkType())
                    .parentChunkId(chunk.metadata().parentChunkId())
                    .previousChunkId(previous == null ? null : previous.id())
                    .nextChunkId(next == null ? null : next.id())
                    .section(chunk.metadata().section())
                    .objectType(chunk.metadata().objectType())
                    .objectId(chunk.metadata().objectId())
                    .startOffset(chunk.metadata().startOffset())
                    .endOffset(chunk.metadata().endOffset())
                    .tokenCount(chunk.metadata().tokenCount())
                    .charCount(chunk.metadata().charCount())
                    .blockIds(chunk.metadata().blockIds())
                    .confidence(chunk.metadata().confidence())
                    .attributes(chunk.metadata().attributes())
                    .build();
            linked.add(Chunk.of(chunk.id(), chunk.content(), metadata));
        }
        return linked;
    }

    private boolean containsTable(List<NormalizedBlock> blocks) {
        return blocks.stream().anyMatch(block -> block.type() == NormalizedBlockType.TABLE);
    }

    private boolean isHeadingOnly(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(block -> block.text() != null && !block.text().isBlank())
                .allMatch(block -> isHeading(block) && isHeadingOnlyText(block.text()));
    }

    private boolean isTocSection(Section section) {
        String heading = normalize(section.headingPath());
        String text = normalize(content(section.blocks()));
        if (heading.contains("목 차") || heading.contains("toc-heading")) {
            return true;
        }
        long linkCount = countOccurrences(text, "](#") + countOccurrences(text, "_toc");
        long articleLinkCount = text.lines()
                .mapToLong(line -> line.contains("제") && (line.contains("장") || line.contains("조") || line.contains("절"))
                        ? countOccurrences(line, "](#")
                        : 0)
                .sum();
        return linkCount >= 5 || articleLinkCount >= 3;
    }

    private long countOccurrences(String value, String pattern) {
        if (value == null || value.isBlank() || pattern == null || pattern.isBlank()) {
            return 0;
        }
        long count = 0;
        int index = 0;
        while ((index = value.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private boolean isHeading(NormalizedBlock block) {
        return block.type() == NormalizedBlockType.TITLE || block.type() == NormalizedBlockType.HEADING;
    }

    private boolean isMarkdownHeadingLine(String line) {
        return line != null && MARKDOWN_HEADING.matcher(line).matches();
    }

    private String cleanHeadingLine(String line) {
        if (line == null) {
            return "";
        }
        Matcher matcher = MARKDOWN_HEADING.matcher(line);
        String cleaned = matcher.matches() ? matcher.group(2) : line;
        return cleaned.replaceAll("\\{#[^}]+}", "")
                .replaceAll("^\\[\\]\\{#[^}]+}\\s*", "")
                .replace("**", "")
                .trim();
    }

    private String resolveHeadingPath(NormalizedBlock block) {
        if (block.headingPath() != null && !block.headingPath().isBlank()) {
            return block.headingPath();
        }
        return block.text();
    }

    private String content(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String blockContent(BlockifyBlock block) {
        StringBuilder builder = new StringBuilder();
        builder.append("제목: ").append(block.title()).append("\n\n");
        builder.append("핵심 질문:\n").append(block.question()).append("\n\n");
        builder.append("답변:\n").append(block.answer());
        List<String> evidenceTexts = block.sourceEvidence() == null ? List.of() : block.sourceEvidence().stream()
                .map(BlockifySourceEvidence::text)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();
        if (!evidenceTexts.isEmpty()) {
            builder.append("\n\n핵심 원문 Evidence:\n");
            for (String evidence : evidenceTexts) {
                builder.append("- ").append(evidence.replaceAll("\\s+", " ").trim()).append("\n");
            }
            if (builder.charAt(builder.length() - 1) == '\n') {
                builder.setLength(builder.length() - 1);
            }
        }
        if (block.keywords() != null && !block.keywords().isEmpty()) {
            builder.append("\n\n키워드:\n").append(String.join(", ", block.keywords()));
        }
        return builder.toString();
    }

    private boolean isHeadingOnlyText(String value) {
        String stripped = stripMarkdownHeading(value);
        if (stripped.isBlank()) {
            return true;
        }
        return stripped.lines().allMatch(line -> line.trim().matches("^(제\\s*)?\\d+\\s*(장|절)$")
                || line.trim().matches("^제\\s*\\d+조\\s*\\([^)]*\\)$"));
    }

    private boolean isGenericQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("에 대해 무엇을 확인해야 하는가")
                || normalized.contains("해당 내용은 무엇인가")
                || normalized.contains("위 사항을 설명")
                || normalized.contains("이것은 무엇인가");
    }

    private String generationFailureReason(RuntimeException ex) {
        if (ex instanceof BlockifyGenerationTimeoutException) {
            return "LLM_GENERATION_TIMEOUT";
        }
        String type = ex.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        if (ex instanceof IllegalStateException && ex.getMessage() != null
                && ex.getMessage().contains("Failed to parse Blockify LLM response")) {
            return "LLM_RESPONSE_PARSE_FAILED";
        }
        return type;
    }

    private List<BlockifyBlock> generateWithTimeout(BlockifyGenerationRequest request) {
        Duration timeout = properties.getGenerationTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return generator.generate(request);
        }
        Future<List<BlockifyBlock>> future = generationExecutor.submit(() -> generator.generate(request));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new BlockifyGenerationTimeoutException(timeout, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IllegalStateException("Blockify generation was interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Blockify generation failed", cause);
        }
    }

    private static final class BlockifyThreadFactory implements ThreadFactory {

        private static final AtomicInteger SEQUENCE = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "blockify-generator-" + SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class BlockifyGenerationTimeoutException extends RuntimeException {

        private BlockifyGenerationTimeoutException(Duration timeout, Throwable cause) {
            super("Blockify LLM generation timed out after " + timeout, cause);
        }
    }

    private String stripMarkdownHeading(String value) {
        return value == null ? "" : value.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("\\{#[^}]+}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> blockIds(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .map(NormalizedBlock::blockIds)
                .flatMap(List::stream)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String effectiveSourceDocumentId(NormalizedDocument document, ChunkingContext context) {
        if (!document.sourceDocumentId().isBlank()) {
            return document.sourceDocumentId();
        }
        return context.sourceDocumentId();
    }

    private String effectiveLlmProvider(NormalizedDocument document, ChunkingContext context) {
        return firstNonBlank(metadataValue(context, KEY_BLOCKIFY_LLM_PROVIDER),
                metadataValue(document, KEY_BLOCKIFY_LLM_PROVIDER),
                properties.getLlmProvider());
    }

    private String effectiveLlmModel(NormalizedDocument document, ChunkingContext context) {
        return firstNonBlank(metadataValue(context, KEY_BLOCKIFY_LLM_MODEL),
                metadataValue(document, KEY_BLOCKIFY_LLM_MODEL),
                properties.getLlmModel());
    }

    private String effectiveGeneratorModel(NormalizedDocument document, ChunkingContext context) {
        return firstNonBlank(effectiveLlmModel(document, context), properties.getGeneratorModel());
    }

    private NormalizedDocument withProfileMetadata(
            NormalizedDocument document,
            BlockifyDocumentTypeClassification classification,
            BlockifyProfile profile) {
        if (document == null || classification == null || profile == null) {
            return document;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put(KEY_REQUESTED_DOCUMENT_TYPE, classification.requestedType().value());
        metadata.put(KEY_DETECTED_DOCUMENT_TYPE, classification.effectiveType().value());
        metadata.put(KEY_DOCUMENT_TYPE_CONFIDENCE, classification.confidence());
        metadata.put(KEY_DOCUMENT_TYPE_REASON, classification.reason());
        metadata.put(KEY_DOCUMENT_TYPE_SIGNALS, classification.signals().toMap());
        metadata.put(KEY_BLOCKIFY_PROFILE, profile.profileId());
        metadata.put(KEY_IDEA_BLOCK_SCHEMA_VERSION, profile.schemaVersion());
        metadata.put("blockifyQuestionStyle", profile.questionStyle());
        return NormalizedDocument.builder(document.sourceDocumentId())
                .plainText(document.plainText())
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(document.blocks())
                .metadata(metadata)
                .build();
    }

    private NormalizedDocument withConfiguredDocumentType(NormalizedDocument document, ChunkingContext context) {
        if (document == null) {
            return null;
        }
        if (metadataValue(context, BlockifyDocumentTypeClassifier.KEY_BLOCKIFY_DOCUMENT_TYPE) != null
                || metadataValue(document, BlockifyDocumentTypeClassifier.KEY_BLOCKIFY_DOCUMENT_TYPE) != null) {
            return document;
        }
        BlockifyDocumentType configured = BlockifyDocumentType.from(properties.getDocumentType());
        if (configured == BlockifyDocumentType.AUTO) {
            return document;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put(BlockifyDocumentTypeClassifier.KEY_BLOCKIFY_DOCUMENT_TYPE, configured.value());
        return NormalizedDocument.builder(document.sourceDocumentId())
                .plainText(document.plainText())
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(document.blocks())
                .metadata(metadata)
                .build();
    }

    private BlockifyDocumentType effectiveDocumentType(NormalizedDocument document) {
        String value = metadataValue(document, KEY_DETECTED_DOCUMENT_TYPE);
        return value == null ? BlockifyDocumentType.GENERAL : BlockifyDocumentType.from(value);
    }

    private Boolean effectivePiiMaskingEnabled(NormalizedDocument document, ChunkingContext context) {
        String value = firstNonBlank(metadataValue(context, KEY_BLOCKIFY_PII_MASKING_ENABLED),
                metadataValue(document, KEY_BLOCKIFY_PII_MASKING_ENABLED));
        return value == null ? null : Boolean.parseBoolean(value);
    }

    private String metadataValue(ChunkingContext context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.metadata().get(key);
        return value == null ? null : value.toString();
    }

    private String metadataValue(NormalizedDocument document, String key) {
        if (document == null || key == null) {
            return null;
        }
        Object value = document.metadata().get(key);
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String sectionId(String sourceDocumentId, int index) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-section-" + index;
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-blockify-" + order;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text.trim();
    }

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        metadata.put(key, value);
    }

    private void putTypedFields(Map<String, Object> metadata, Map<String, Object> typedFields) {
        if (typedFields == null || typedFields.isEmpty()) {
            return;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        typedFields.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                sanitized.put(key, value);
            }
        });
        if (sanitized.isEmpty()) {
            return;
        }
        metadata.put("typedFields", Map.copyOf(sanitized));
        sanitized.forEach((key, value) -> put(metadata, key, value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Section(String sectionId, String headingPath, int startOrder, int endOrder,
                           List<NormalizedBlock> blocks) {
    }

    private record BlockCursor(int blockIndex, int order) {
    }

    private record ChunkingSummary(
            int chunkCount,
            int ideaBlockCount,
            int fallbackCount,
            Map<String, Integer> fallbackReasonCounts,
            int sourceBlockTargetCount,
            int sourceBlockCoveredCount,
            double sourceBlockCoverage,
            Double averageConfidence) {
    }

    private record SimilarityCluster(String clusterId, int memberCount, double maxScore) {
    }

    private static final class DisjointSet {
        private final int[] parent;

        private DisjointSet(int size) {
            this.parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot;
            }
        }
    }
}
