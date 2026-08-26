package studio.one.platform.markdown.autoconfigure;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.documentmetadata.BuiltInDocumentMetadataSchemaRegistry;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataClassification;
import studio.one.platform.documentmetadata.DocumentMetadataEvidence;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataFieldDescriptor;
import studio.one.platform.documentmetadata.DocumentMetadataProvenance;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;
import studio.one.platform.documentmetadata.DocumentMetadataSchema;
import studio.one.platform.documentmetadata.DocumentMetadataSchemaRegistry;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.documentmetadata.DocumentSemanticTypeSelection;
import studio.one.platform.documentmetadata.MetadataEnrichmentMode;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.MarkdownMetadataEnrichmentException;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownMetadataEnrichmentPort;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;

final class DefaultMarkdownMetadataEnrichmentService implements MarkdownMetadataEnrichmentPort {

    private static final String EXTRACTOR_VERSION = "document-metadata-native-v2";
    private static final Set<String> INSIGHT_FIELDS = Set.of("summary", "keywords");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+\\d{1,3}[ -]?)?(?:\\d{2,4}[- ]\\d{3,4}[- ]\\d{4})(?!\\d)");
    private static final Pattern GOVERNMENT_IDENTIFIER = Pattern.compile(
            "(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");
    private static final Set<String> EXCLUDED_FIELD_PARTS = Set.of(
            "email", "phone", "mobile", "address", "account", "password",
            "resident", "socialsecurity", "creditcard", "payment");
    private static final Map<String, String> NATIVE_KEYS = Map.ofEntries(
            Map.entry("title", "title"),
            Map.entry("subtitle", "subtitle"),
            Map.entry("author", "authors"),
            Map.entry("authors", "authors"),
            Map.entry("creator", "creators"),
            Map.entry("creators", "creators"),
            Map.entry("publisher", "publisher"),
            Map.entry("publicationdate", "publicationDate"),
            Map.entry("publisheddate", "publishedDate"),
            Map.entry("date", "dates"),
            Map.entry("language", "language"),
            Map.entry("keywords", "keywords"),
            Map.entry("subject", "subjects"),
            Map.entry("subjects", "subjects"),
            Map.entry("isbn", "isbn"),
            Map.entry("doi", "doi"),
            Map.entry("abstract", "abstract"),
            Map.entry("summary", "summary"),
            Map.entry("organization", "organization"),
            Map.entry("institution", "institution"),
            Map.entry("affiliation", "affiliations"),
            Map.entry("affiliations", "affiliations"));

    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;
    private final DocumentMetadataSchemaRegistry schemas;
    private final ModelDeploymentRegistry deployments;
    private final MarkdownProperties.Metadata properties;
    private final String systemPrompt;

    DefaultMarkdownMetadataEnrichmentService(
            MarkdownRepository repository,
            ObjectMapper objectMapper,
            DocumentMetadataSchemaRegistry schemas,
            ModelDeploymentRegistry deployments,
            MarkdownProperties.Metadata properties) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.deployments = deployments;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.systemPrompt = loadPrompt(properties.getPromptResource());
        validateConfiguredDeployment();
    }

    @Override
    public void enrich(MarkdownRevision revision, MarkdownPipelineOptions options) {
        DocumentMetadataArtifact artifact = preview(revision, options);
        if (existingArtifact(revision.revisionId())
                .filter(existing -> artifact.fingerprint().equals(existing.fingerprint()))
                .isPresent()) {
            return;
        }
        repository.upsertResource(new MarkdownResource(
                artifact.artifactId(),
                revision.revisionId(),
                MarkdownDocumentMetadataService.RESOURCE_TYPE,
                MarkdownDocumentMetadataService.RESOURCE_NAME,
                null,
                write(artifact)));
    }

    @Override
    public DocumentMetadataArtifact preview(MarkdownRevision revision, MarkdownPipelineOptions options) {
        return generate(revision, options, false);
    }

    @Override
    public DocumentMetadataArtifact regenerate(MarkdownRevision revision, MarkdownPipelineOptions options) {
        DocumentMetadataArtifact artifact = generate(revision, options, true);
        repository.upsertResource(new MarkdownResource(
                artifact.artifactId(),
                revision.revisionId(),
                MarkdownDocumentMetadataService.RESOURCE_TYPE,
                MarkdownDocumentMetadataService.RESOURCE_NAME,
                null,
                write(artifact)));
        return artifact;
    }

    private DocumentMetadataArtifact generate(
            MarkdownRevision revision,
            MarkdownPipelineOptions options,
            boolean force) {
        NormalizedDocument document = normalizedDocument(revision.revisionId());
        String fingerprint = fingerprint(revision, document, options);
        Optional<DocumentMetadataArtifact> existing = existingArtifact(revision.revisionId());
        if (!force && existing.filter(artifact -> fingerprint.equals(artifact.fingerprint())).isPresent()) {
            return existing.orElseThrow();
        }

        DocumentSemanticTypeSelection requested = options.semanticTypeSelection();
        Detection detection = detect(document, requested);
        Map<String, DocumentMetadataField> fields = nativeAndStructuralFields(document, detection.type());
        List<String> warnings = new ArrayList<>();
        MetadataEnrichmentMode mode = options.enrichmentMode();

        if (mode != MetadataEnrichmentMode.OFF && needsLlm(detection, fields)) {
            try {
                LlmResult llm = enrichWithLlm(document, detection.type(), force);
                detection = detection.merge(llm);
                mergeMissing(fields, llm.fields());
                if (mode == MetadataEnrichmentMode.REQUIRED && missingInsights(fields)) {
                    throw MarkdownMetadataEnrichmentException.invalidResponse(
                            properties.getLlmDeploymentId(),
                            new IllegalStateException("Required summary or keywords are missing"));
                }
            } catch (RuntimeException ex) {
                if (mode == MetadataEnrichmentMode.REQUIRED) {
                    if (ex instanceof MarkdownMetadataEnrichmentException classified) {
                        throw classified;
                    }
                    throw MarkdownMetadataEnrichmentException.upstreamUnavailable(
                            properties.getLlmDeploymentId(), ex);
                }
                warnings.add("LLM_ENRICHMENT_FAILED");
            }
        }

        DocumentMetadataClassification classification = new DocumentMetadataClassification(
                options.requestedDocumentProfile(),
                options.resolvedDocumentProfile(),
                requested,
                detection.type(),
                null,
                detection.subject(),
                detection.confidence(),
                detection.classifierVersion(),
                null, null, null, null);
        DocumentMetadataQuality quality = quality(detection.type(), fields, warnings);
        String artifactId = resourceId(revision.revisionId());
        return new DocumentMetadataArtifact(
                artifactId,
                revision.revisionId(),
                schemas.schemaVersion(),
                EXTRACTOR_VERSION,
                fingerprint,
                classification,
                quality,
                fields,
                warnings);
    }

    private NormalizedDocument normalizedDocument(String revisionId) {
        MarkdownResource resource = repository.findResource(
                        revisionId, MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT)
                .orElseThrow(() -> new IllegalStateException(
                        "Normalized document is unavailable for metadata enrichment: " + revisionId));
        return NormalizedDocumentSnapshot.read(resource, objectMapper)
                .map(NormalizedDocumentSnapshot.Snapshot::document)
                .orElseThrow(() -> new IllegalStateException(
                        "Normalized document snapshot is invalid: " + revisionId));
    }

    private Map<String, DocumentMetadataField> nativeAndStructuralFields(
            NormalizedDocument document, DocumentSemanticType type) {
        DocumentMetadataSchema schema = schemas.require(type);
        Map<String, DocumentMetadataField> fields = new LinkedHashMap<>();
        document.metadata().forEach((key, value) -> {
            String canonicalKey = canonicalKey(key);
            String fieldId = NATIVE_KEYS.get(canonicalKey);
            if (fieldId == null || !schema.allows(fieldId) || excluded(fieldId)) {
                return;
            }
            List<String> values = safeValues(value, fieldId);
            if (!values.isEmpty()) {
                List<DocumentMetadataEvidence> evidence = normalizedEvidence(document, values);
                fields.putIfAbsent(fieldId, field(fieldId, values, 0.98d,
                        evidence.isEmpty()
                                ? DocumentMetadataProvenance.NATIVE_STRUCTURED
                                : DocumentMetadataProvenance.SOURCE_VERIFIED,
                        evidence.isEmpty() ? metadataEvidence(values, key) : evidence));
            }
        });

        if (!fields.containsKey("title")) {
            document.blocks().stream()
                    .filter(block -> block.type() == NormalizedBlockType.TITLE
                            || block.type() == NormalizedBlockType.HEADING)
                    .filter(NormalizedBlock::hasText)
                    .findFirst()
                    .ifPresent(block -> fields.put("title", field(
                            "title", List.of(block.text()), 0.84d,
                            DocumentMetadataProvenance.STRUCTURAL_HEURISTIC,
                            List.of(evidence(block, block.text())))));
        }
        if (!fields.containsKey("title") && hasText(document.filename())) {
            String title = filenameTitle(document.filename());
            if (hasText(title)) {
                fields.put("title", field("title", List.of(title), 0.65d,
                        DocumentMetadataProvenance.STRUCTURAL_HEURISTIC, List.of()));
            }
        }
        return fields;
    }

    private Detection detect(NormalizedDocument document, DocumentSemanticTypeSelection requested) {
        if (requested.explicitType() != null) {
            return new Detection(requested.explicitType(), subject(document, requested.explicitType()),
                    1.0d, "user-selection-v1");
        }
        String format = canonicalKey(document.sourceFormat());
        String sample = sample(document).toLowerCase(Locale.ROOT);
        if ("epub".equals(format) || format.contains("epub")) {
            return new Detection(DocumentSemanticType.BOOK, subject(document, DocumentSemanticType.BOOK),
                    0.92d, "native-rules-v1");
        }
        if (containsAny(sample, "doi:", "abstract", "초록", "keywords", "키워드")) {
            return new Detection(DocumentSemanticType.ACADEMIC_PAPER, subject(document,
                    DocumentSemanticType.ACADEMIC_PAPER), 0.82d, "structural-rules-v1");
        }
        if (containsAny(sample, "학위논문", "doctoral thesis", "master's thesis", "지도교수")) {
            return new Detection(DocumentSemanticType.THESIS, subject(document, DocumentSemanticType.THESIS),
                    0.90d, "structural-rules-v1");
        }
        if (containsAny(sample, "시행일", "제1조", "policy", "regulation")) {
            return new Detection(DocumentSemanticType.POLICY, null, 0.82d, "structural-rules-v1");
        }
        if (containsAny(sample, "사용 설명서", "user manual", "installation guide")) {
            return new Detection(DocumentSemanticType.MANUAL, null, 0.82d, "structural-rules-v1");
        }
        if (format.contains("ppt") || format.contains("presentation")) {
            return new Detection(DocumentSemanticType.PRESENTATION, null, 0.90d, "native-rules-v1");
        }
        if (containsAny(sample, "보고서", "research report", "annual report", "executive summary")) {
            return new Detection(DocumentSemanticType.REPORT, subject(document, DocumentSemanticType.REPORT),
                    0.82d, "structural-rules-v1");
        }
        return new Detection(DocumentSemanticType.GENERAL, subject(document, DocumentSemanticType.GENERAL),
                0.55d, "fallback-rules-v1");
    }

    private boolean needsLlm(Detection detection, Map<String, DocumentMetadataField> fields) {
        if (detection.confidence() < properties.getTypeConfidenceThreshold()) {
            return true;
        }
        boolean schemaGap = schemas.require(detection.type()).fields().stream()
                .filter(field -> field.required() || field.recommended())
                .anyMatch(descriptor -> {
                    DocumentMetadataField value = fields.get(descriptor.fieldId());
                    return value == null || value.confidence() < properties.getFieldConfidenceThreshold();
                });
        if (schemaGap) {
            return true;
        }
        return INSIGHT_FIELDS.stream().anyMatch(fieldId -> needsInsightField(fieldId, fields.get(fieldId)));
    }

    private boolean needsInsightField(String fieldId, DocumentMetadataField field) {
        if (field == null || field.normalizedValues().isEmpty()) {
            return true;
        }
        return "keywords".equals(fieldId) && field.confidence() < properties.getFieldConfidenceThreshold();
    }

    private boolean missingInsights(Map<String, DocumentMetadataField> fields) {
        return INSIGHT_FIELDS.stream().anyMatch(fieldId -> {
            DocumentMetadataField field = fields.get(fieldId);
            return field == null || field.normalizedValues().isEmpty();
        });
    }

    private LlmResult enrichWithLlm(
            NormalizedDocument document,
            DocumentSemanticType currentType,
            boolean insightsOnly) {
        ModelDeployment deployment = requireDeployment();
        String dossier = dossier(document, schemas.require(currentType), insightsOnly);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(dossier)))
                .temperature(0.0d)
                .maxOutputTokens(4096)
                .responseMimeType("application/json")
                .responseSchema(metadataResponseSchema(currentType, insightsOnly))
                .build();
        String content;
        try {
            content = deployments.chatPort(deployment.deploymentId()).chat(request).messages().stream()
                    .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                    .map(ChatMessage::content)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Metadata model returned no assistant message"));
        } catch (RuntimeException ex) {
            throw MarkdownMetadataEnrichmentException.upstreamUnavailable(deployment.deploymentId(), ex);
        }
        return parseLlm(content, document, currentType);
    }

    private LlmResult parseLlm(String content, NormalizedDocument document, DocumentSemanticType fallbackType) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(content));
            DocumentSemanticType type = parseType(root.path("semanticType").asText(), fallbackType);
            double confidence = clamp(root.path("confidence").asDouble(0.0d));
            String subject = safeSubject(root.path("subject").isNull() ? null : root.path("subject").asText());
            DocumentMetadataSchema schema = schemas.require(type);
            Map<String, DocumentMetadataField> fields = new LinkedHashMap<>();
            JsonNode fieldRoot = root.path("fields");
            if (fieldRoot.isObject()) {
                fieldRoot.properties().forEach(entry -> {
                    String fieldId = entry.getKey();
                    if (!schema.allows(fieldId) || excluded(fieldId)) {
                        return;
                    }
                    JsonNode fieldNode = entry.getValue();
                    List<String> values = jsonValues(fieldNode.path("values"), fieldId);
                    if (values.isEmpty()) {
                        return;
                    }
                    String evidenceText = safeText(fieldNode.path("evidenceText").asText(null), 500);
                    Optional<NormalizedBlock> block = findExactBlock(document, evidenceText);
                    boolean sourceVerified = block.isPresent() && valuesSupported(values, evidenceText);
                    DocumentMetadataProvenance provenance = sourceVerified
                            ? DocumentMetadataProvenance.SOURCE_VERIFIED
                            : DocumentMetadataProvenance.INFERRED;
                    List<DocumentMetadataEvidence> evidence = sourceVerified
                            ? block
                            .map(value -> List.of(evidence(value, evidenceText)))
                            .orElseGet(List::of)
                            : List.of();
                    fields.put(fieldId, field(fieldId, values,
                            sourceVerified ? Math.max(0.85d, confidence) : Math.min(0.49d, confidence),
                            provenance, evidence));
                });
            }
            return new LlmResult(type, subject, confidence, fields);
        } catch (RuntimeException ex) {
            throw MarkdownMetadataEnrichmentException.invalidResponse(
                    properties.getLlmDeploymentId(), ex);
        }
    }

    private String metadataResponseSchema(DocumentSemanticType currentType, boolean insightsOnly) {
        Map<String, Object> valueSchema = Map.of("type", "string");
        Map<String, Object> fieldSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "values", Map.of("type", "array", "items", valueSchema),
                        "evidenceText", valueSchema),
                "required", List.of("values", "evidenceText"));
        Map<String, Object> fieldProperties = new LinkedHashMap<>();
        (insightsOnly
                ? INSIGHT_FIELDS.stream()
                : schemas.require(currentType).fields().stream().map(DocumentMetadataFieldDescriptor::fieldId))
                .distinct()
                .sorted()
                .forEach(fieldId -> fieldProperties.put(fieldId, fieldSchema));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("semanticType", Map.of(
                "type", "string",
                "enum", List.of("GENERAL", "BOOK", "ACADEMIC_PAPER", "THESIS", "REPORT",
                        "POLICY", "MANUAL", "PRESENTATION", "UNKNOWN")));
        properties.put("subject", Map.of("type", "string", "nullable", true));
        properties.put("confidence", Map.of("type", "number", "minimum", 0.0d, "maximum", 1.0d));
        properties.put("fields", Map.of(
                "type", "object",
                "properties", fieldProperties,
                "required", List.of("summary", "keywords")));
        return write(Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("semanticType", "confidence", "fields")));
    }

    private String dossier(
            NormalizedDocument document,
            DocumentMetadataSchema schema,
            boolean insightsOnly) {
        StringBuilder result = new StringBuilder();
        result.append("Allowed fields: ")
                .append(insightsOnly
                        ? INSIGHT_FIELDS.stream().sorted().toList()
                        : schema.fields().stream().map(DocumentMetadataFieldDescriptor::fieldId).toList())
                .append("\\nOutput language: ")
                .append(outputLanguage(document))
                .append(". Write summary prose and keywords in this language.")
                .append("\\nBlocks:\\n");
        int count = 0;
        for (NormalizedBlock block : document.blocks()) {
            if (count >= properties.getMaxBlocks() || result.length() >= properties.getMaxCharacters()) {
                break;
            }
            if (block.page() != null && block.page() > properties.getMaxPages()) {
                continue;
            }
            String line = "[blockId=" + block.id() + ", sourceRef=" + nullToEmpty(block.sourceRef())
                    + ", page=" + nullToEmpty(block.page()) + "] " + block.text() + "\\n";
            int remaining = properties.getMaxCharacters() - result.length();
            result.append(line, 0, Math.min(remaining, line.length()));
            count++;
        }
        return result.toString();
    }

    private static String outputLanguage(NormalizedDocument document) {
        Optional<String> metadataLanguage = document.metadata().entrySet().stream()
                .filter(entry -> "language".equals(canonicalKey(entry.getKey())))
                .map(Map.Entry::getValue)
                .flatMap(value -> safeValues(value, "language").stream())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .findFirst();
        String language = metadataLanguage.orElseGet(() -> containsHangul(sample(document)) ? "ko" : "en");
        if (language.startsWith("ko") || language.startsWith("kor")) {
            return "Korean (ko)";
        }
        if (language.startsWith("ja") || language.startsWith("jpn")) {
            return "Japanese (ja)";
        }
        if (language.startsWith("zh") || language.startsWith("chi") || language.startsWith("zho")) {
            return "Chinese (zh)";
        }
        return "English (en)";
    }

    private static boolean containsHangul(String value) {
        return value != null && value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                        || (codePoint >= 0x3131 && codePoint <= 0x318E));
    }

    private DocumentMetadataQuality quality(DocumentSemanticType type,
            Map<String, DocumentMetadataField> fields, List<String> warnings) {
        boolean missingRequired = schemas.require(type).fields().stream()
                .filter(DocumentMetadataFieldDescriptor::required)
                .anyMatch(descriptor -> fields.get(descriptor.fieldId()) == null
                        || !fields.get(descriptor.fieldId()).sourceVerified());
        if (!warnings.isEmpty()) {
            return DocumentMetadataQuality.WARNING;
        }
        return missingRequired ? DocumentMetadataQuality.PARTIAL : DocumentMetadataQuality.COMPLETE;
    }

    private Optional<DocumentMetadataArtifact> existingArtifact(String revisionId) {
        return repository.findResource(revisionId, MarkdownDocumentMetadataService.RESOURCE_TYPE)
                .flatMap(resource -> {
                    try {
                        return Optional.of(objectMapper.readValue(
                                resource.metadataJson(), DocumentMetadataArtifact.class));
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                });
    }

    private ModelDeployment requireDeployment() {
        if (deployments == null) {
            throw MarkdownMetadataEnrichmentException.modelConfiguration(
                    properties.getLlmDeploymentId(), null);
        }
        String deploymentId = safeText(properties.getLlmDeploymentId(), 200);
        return deployments.find(deploymentId)
                .filter(ModelDeployment::enabled)
                .filter(deployment -> deployment.workload() == ModelWorkload.CHAT)
                .filter(deployment -> deployment.definition().structuredOutput())
                .orElseThrow(() -> MarkdownMetadataEnrichmentException.modelConfiguration(deploymentId, null));
    }

    private void validateConfiguredDeployment() {
        if (deployments != null && hasText(properties.getLlmDeploymentId())) {
            requireDeployment();
        }
    }

    private String fingerprint(MarkdownRevision revision, NormalizedDocument document,
            MarkdownPipelineOptions options) {
        String payload = nullToEmpty(revision.contentHash()) + "\n"
                + sha256(document.chunkableText()) + "\n"
                + schemas.schemaVersion() + "\n"
                + EXTRACTOR_VERSION + "\n"
                + sha256(systemPrompt) + "\n"
                + options.semanticTypeSelection().name() + "\n"
                + options.enrichmentMode().name() + "\n"
                + nullToEmpty(properties.getLlmDeploymentId());
        return sha256(payload);
    }

    private static void mergeMissing(Map<String, DocumentMetadataField> target,
            Map<String, DocumentMetadataField> candidates) {
        candidates.forEach(target::putIfAbsent);
    }

    private static DocumentMetadataField field(String fieldId, List<String> values, double confidence,
            DocumentMetadataProvenance provenance, List<DocumentMetadataEvidence> evidence) {
        return new DocumentMetadataField(fieldId, values, values, clamp(confidence), provenance, evidence);
    }

    private static List<DocumentMetadataEvidence> metadataEvidence(List<String> values, String key) {
        return values.stream().limit(DocumentMetadataField.MAX_EVIDENCE_COUNT)
                .map(value -> new DocumentMetadataEvidence(value, "metadata:" + key, null,
                        null, null, null, null, null))
                .toList();
    }

    private static List<DocumentMetadataEvidence> normalizedEvidence(
            NormalizedDocument document,
            List<String> values) {
        return values.stream()
                .limit(DocumentMetadataField.MAX_EVIDENCE_COUNT)
                .map(value -> findExactBlock(document, value)
                        .map(block -> evidence(block, value)))
                .flatMap(Optional::stream)
                .toList();
    }

    private static DocumentMetadataEvidence evidence(NormalizedBlock block, String text) {
        int start = block.text().indexOf(text);
        Integer startOffset = start < 0 ? null : start;
        Integer endOffset = start < 0 ? null : start + text.length();
        return new DocumentMetadataEvidence(text, block.sourceRef(), block.id(), block.page(), block.slide(),
                block.headingPath(), startOffset, endOffset);
    }

    private static Optional<NormalizedBlock> findExactBlock(NormalizedDocument document, String exactText) {
        if (!hasText(exactText)) {
            return Optional.empty();
        }
        return document.blocks().stream()
                .filter(block -> block.hasText() && block.text().contains(exactText))
                .findFirst();
    }

    static boolean valuesSupported(List<String> values, String evidenceText) {
        if (values == null || values.isEmpty() || !hasText(evidenceText)) {
            return false;
        }
        String normalizedEvidence = normalizeForMatch(evidenceText);
        return values.stream()
                .map(DefaultMarkdownMetadataEnrichmentService::normalizeForMatch)
                .allMatch(value -> !value.isBlank() && normalizedEvidence.contains(value));
    }

    private static String normalizeForMatch(String value) {
        return value == null ? "" : java.text.Normalizer
                .normalize(value, java.text.Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static List<String> safeValues(Object value, String fieldId) {
        List<String> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addSafe(values, item, fieldId));
        } else {
            addSafe(values, value, fieldId);
        }
        return values.stream().distinct().limit(20).toList();
    }

    private static List<String> jsonValues(JsonNode node, String fieldId) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> addSafe(values, item.asText(null), fieldId));
        } else if (node.isTextual()) {
            addSafe(values, node.asText(), fieldId);
        }
        return values.stream().distinct().limit(20).toList();
    }

    private static void addSafe(List<String> values, Object raw, String fieldId) {
        if (raw == null || excluded(fieldId)) {
            return;
        }
        String value = safeText(raw.toString(), longField(fieldId) ? 5000 : 1000);
        if (!hasText(value) || isSensitiveValue(value)) {
            return;
        }
        if (fieldId.toLowerCase(Locale.ROOT).contains("date")) {
            value = normalizePartialDate(value);
        }
        if (hasText(value)) {
            values.add(value);
        }
    }

    static boolean isSensitiveValue(String value) {
        return hasText(value) && (EMAIL.matcher(value).find()
                || PHONE.matcher(value).find()
                || GOVERNMENT_IDENTIFIER.matcher(value).find());
    }

    private static String normalizePartialDate(String value) {
        String normalized = value.trim();
        if (normalized.matches("\\d{4}")) {
            return normalized;
        }
        if (normalized.matches("\\d{4}[-./]\\d{1,2}")) {
            String[] parts = normalized.split("[-./]");
            return "%s-%02d".formatted(parts[0], Integer.parseInt(parts[1]));
        }
        if (normalized.matches("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}.*")) {
            String[] parts = normalized.substring(0, 10).split("[-./]");
            return "%s-%02d-%02d".formatted(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return normalized;
    }

    private static String subject(NormalizedDocument document, DocumentSemanticType type) {
        if (type != DocumentSemanticType.BOOK && type != DocumentSemanticType.ACADEMIC_PAPER
                && type != DocumentSemanticType.THESIS && type != DocumentSemanticType.REPORT
                && type != DocumentSemanticType.GENERAL) {
            return null;
        }
        String sample = sample(document).toLowerCase(Locale.ROOT);
        return containsAny(sample, "인문", "철학", "역사", "문학", "humanities", "philosophy",
                "history", "literature") ? "HUMANITIES" : null;
    }

    private static String sample(NormalizedDocument document) {
        String text = document.chunkableText();
        return text.substring(0, Math.min(12_000, text.length()));
    }

    private static DocumentSemanticType parseType(String value, DocumentSemanticType fallback) {
        if (!hasText(value) || "AUTO".equalsIgnoreCase(value)) {
            return fallback;
        }
        try {
            return DocumentSemanticType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static boolean excluded(String key) {
        String canonical = canonicalKey(key);
        return EXCLUDED_FIELD_PARTS.stream().anyMatch(canonical::contains);
    }

    private static boolean longField(String fieldId) {
        return fieldId.toLowerCase(Locale.ROOT).contains("summary")
                || fieldId.equalsIgnoreCase("abstract");
    }

    private static String canonicalKey(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String filenameTitle(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').trim();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize document metadata JSON", ex);
        }
    }

    private static String resourceId(String revisionId) {
        UUID uuid = UUID.nameUUIDFromBytes(
                ("document-metadata:" + revisionId).getBytes(StandardCharsets.UTF_8));
        return "mres-metadata-" + uuid;
    }

    private static String loadPrompt(String location) {
        String path = hasText(location) ? location.trim() : "classpath:prompts/document-metadata.v1.prompt";
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (InputStream input = DefaultMarkdownMetadataEnrichmentService.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Document metadata prompt not found: " + location);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to read document metadata prompt: " + location, ex);
        }
    }

    private static String stripFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    static String extractJsonObject(String value) {
        String text = stripFence(value);
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return text.substring(start, index + 1);
            }
        }
        return text.substring(start);
    }

    private static String safeSubject(String value) {
        String normalized = safeText(value, 80);
        return normalized == null ? null
                : normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
    }

    private static String safeText(String value, int maxLength) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.substring(0, Math.min(maxLength, normalized.length()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Detection(DocumentSemanticType type, String subject, double confidence,
            String classifierVersion) {
        Detection merge(LlmResult llm) {
            if (llm.confidence() <= confidence) {
                return this;
            }
            return new Detection(llm.type(), llm.subject(), llm.confidence(), "llm-source-verified-v1");
        }
    }

    private record LlmResult(DocumentSemanticType type, String subject, double confidence,
            Map<String, DocumentMetadataField> fields) {
    }
}
