package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelDimensionPolicy;
import studio.one.platform.ai.model.ModelDistribution;
import studio.one.platform.ai.model.ModelLifecycle;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.Modality;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.documentmetadata.BuiltInDocumentMetadataSchemaRegistry;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.markdown.application.MarkdownMetadataEnrichmentException;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class DefaultMarkdownMetadataEnrichmentServiceTest {

    @Test
    void sourceVerificationRequiresEveryProposedValueToAppearInTheExactEvidence() {
        assertThat(DefaultMarkdownMetadataEnrichmentService.valuesSupported(
                List.of("J. D. Salinger", "1951"),
                "J. D. Salinger, published in 1951")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.valuesSupported(
                List.of("J. D. Salinger", "1965"),
                "J. D. Salinger, published in 1951")).isFalse();
    }

    @Test
    void rejectsDirectContactAndGovernmentIdentifiers() {
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("author@example.com")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("010-1234-5678")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("900101-1234567")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("ISBN 9780316769488")).isFalse();
    }

    @Test
    void classifiesHumanitiesEpubAsBookAndPersistsNativeMetadataOnce() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("epub")
                .filename("humanities.epub")
                .plainText("인문학의 역사 철학과 문학")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "인문학의 역사")
                        .id("block-1")
                        .sourceRef("epub:chapter.xhtml#element[0]")
                        .order(0)
                        .build()))
                .metadata(Map.of(
                        "title", "인문학의 역사",
                        "authors", List.of("홍길동"),
                        "publisher", "테스트 출판사",
                        "publicationDate", "2026-07-24",
                        "email", "do-not-store@example.com"))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                properties.getMetadata());
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null, null, null, null,
                null, null, null, null,
                false, null, false, null, null, null,
                null, null, null, null,
                "GENERAL_DOCUMENT", null, null, null,
                "AUTO", "OFF");

        service.enrich(revision(), options);

        ArgumentCaptor<MarkdownResource> captor = ArgumentCaptor.forClass(MarkdownResource.class);
        verify(repository).upsertResource(captor.capture());
        MarkdownResource resource = captor.getValue();
        DocumentMetadataArtifact artifact = objectMapper.readValue(
                resource.metadataJson(), DocumentMetadataArtifact.class);
        assertThat(artifact.classification().effectiveSemanticType()).isEqualTo(DocumentSemanticType.BOOK);
        assertThat(artifact.classification().subject()).isEqualTo("HUMANITIES");
        assertThat(artifact.fields()).containsKeys("title", "authors", "publisher", "publicationDate");
        assertThat(artifact.fields()).doesNotContainKey("email");
        assertThat(resource.resourceId()).startsWith("mres-metadata-");
    }

    @Test
    void keepsNativeKeywordsAndOnlyFillsMissingInsightsFromLlm() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("pdf")
                .filename("policy.pdf")
                .plainText("권한 정책의 적용 범위와 승인 절차를 설명한다.")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "권한 정책")
                        .id("block-1")
                        .sourceRef("page[1]/block[1]")
                        .order(0)
                        .build()))
                .metadata(Map.of(
                        "title", "권한 정책",
                        "keywords", List.of("네이티브 키워드")))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        ChatPort chatPort = mock(ChatPort.class);
        when(chatPort.chat(any())).thenReturn(new ChatResponse(List.of(ChatMessage.assistant("""
                {
                  "semanticType": "POLICY",
                  "subject": null,
                  "confidence": 0.92,
                  "fields": {
                    "keywords": {
                      "values": ["LLM 키워드"],
                      "evidenceText": "권한 정책"
                    },
                    "summary": {
                      "values": ["권한 정책의 승인 절차를 간단히 정리한 요약"],
                      "evidenceText": "권한 정책"
                    }
                  }
                }
                """)), "test-model", Map.of()));
        ModelDeploymentRegistry deployments = mock(ModelDeploymentRegistry.class);
        when(deployments.find("chat-default")).thenReturn(Optional.of(chatDeployment()));
        when(deployments.chatPort("chat-default")).thenReturn(chatPort);

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                deployments,
                properties.getMetadata());

        DocumentMetadataArtifact artifact = service.preview(revision(), autoOptions());

        assertThat(artifact.extractorVersion()).isEqualTo("document-metadata-native-v2");
        assertThat(artifact.fields().get("keywords").normalizedValues()).containsExactly("네이티브 키워드");
        assertThat(artifact.fields()).containsKey("summary");
        ArgumentCaptor<studio.one.platform.ai.core.chat.ChatRequest> request =
                ArgumentCaptor.forClass(studio.one.platform.ai.core.chat.ChatRequest.class);
        verify(chatPort).chat(request.capture());
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(4096);
        assertThat(request.getValue().responseMimeType()).isEqualTo("application/json");
        assertThat(request.getValue().messages().get(0).content())
                .contains("Write `summary` and `keywords` in the document's primary language.");
        JsonNode responseSchema = objectMapper.readTree(request.getValue().responseSchema());
        assertThat(responseSchema.path("properties").path("fields").path("properties").has("summary")).isTrue();
        assertThat(responseSchema.path("properties").path("fields").path("properties").has("keywords")).isTrue();
        assertThat(responseSchema.path("properties").path("fields").path("properties").has("doi")).isFalse();
    }

    @Test
    void fingerprintChangesWhenPromptResourceChanges() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("pdf")
                .filename("sample.pdf")
                .plainText("sample document text")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "Sample Document")
                        .id("block-1")
                        .sourceRef("page[1]/block[1]")
                        .order(0)
                        .build()))
                .metadata(Map.of("title", "Sample Document"))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        MarkdownProperties defaultProperties = new MarkdownProperties();
        MarkdownProperties alternateProperties = new MarkdownProperties();
        alternateProperties.getMetadata().setPromptResource("classpath:prompts/document-metadata-alt.prompt");

        DefaultMarkdownMetadataEnrichmentService defaultService = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                defaultProperties.getMetadata());
        DefaultMarkdownMetadataEnrichmentService alternateService = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                alternateProperties.getMetadata());

        DocumentMetadataArtifact first = defaultService.preview(revision(), offOptions());
        DocumentMetadataArtifact second = alternateService.preview(revision(), offOptions());

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void autoModeKeepsArtifactAsWarningWhenInsightEnrichmentFails() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("pdf")
                .filename("warning.pdf")
                .plainText("기본 정보만 있는 문서")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "기본 문서")
                        .id("block-1")
                        .sourceRef("page[1]/block[1]")
                        .order(0)
                        .build()))
                .metadata(Map.of(
                        "title", "기본 문서",
                        "keywords", List.of("기본 키워드")))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                properties.getMetadata());

        DocumentMetadataArtifact artifact = service.preview(revision(), autoOptions());

        assertThat(artifact.quality().name()).isEqualTo("WARNING");
        assertThat(artifact.warnings()).contains("LLM_ENRICHMENT_FAILED");
    }

    @Test
    void regenerationRetriesLlmEvenWhenTheStoredFingerprintMatches() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("epub")
                .filename("retry.epub")
                .plainText("인공지능 전문가들의 연구와 미래 전망을 다룬 문서")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "AI 전문가 인터뷰")
                        .id("block-1")
                        .sourceRef("epub:chapter.xhtml#element[0]")
                        .order(0)
                        .build()))
                .metadata(Map.of("title", "AI 전문가 인터뷰", "language", "ko"))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        ChatPort chatPort = mock(ChatPort.class);
        when(chatPort.chat(any()))
                .thenThrow(new IllegalStateException("temporary model failure"))
                .thenReturn(new ChatResponse(List.of(ChatMessage.assistant("""
                        {
                          "semanticType": "BOOK",
                          "subject": "TECHNOLOGY",
                          "confidence": 0.94,
                          "fields": {
                            "summary": {
                              "values": ["인공지능 전문가들의 연구와 미래 전망을 정리한 문서"],
                              "evidenceText": "인공지능 전문가들의 연구와 미래 전망"
                            },
                            "keywords": {
                              "values": ["인공지능", "전문가", "미래 전망"],
                              "evidenceText": "인공지능 전문가들의 연구와 미래 전망"
                            }
                          }
                        }
                        """)), "test-model", Map.of()));
        ModelDeploymentRegistry deployments = mock(ModelDeploymentRegistry.class);
        when(deployments.find("chat-default")).thenReturn(Optional.of(chatDeployment()));
        when(deployments.chatPort("chat-default")).thenReturn(chatPort);

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                deployments,
                properties.getMetadata());

        DocumentMetadataArtifact failed = service.preview(revision(), autoOptions());
        assertThat(failed.warnings()).contains("LLM_ENRICHMENT_FAILED");
        MarkdownResource stored = new MarkdownResource(
                failed.artifactId(), "mrev-1", "DOCUMENT_METADATA", "document-metadata.json", null,
                objectMapper.writeValueAsString(failed));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.of(stored));

        assertThat(service.preview(revision(), autoOptions()).warnings()).contains("LLM_ENRICHMENT_FAILED");
        DocumentMetadataArtifact regenerated = service.regenerate(revision(), autoOptions());

        assertThat(regenerated.warnings()).doesNotContain("LLM_ENRICHMENT_FAILED");
        assertThat(regenerated.fields()).containsKeys("summary", "keywords");
        ArgumentCaptor<studio.one.platform.ai.core.chat.ChatRequest> requests =
                ArgumentCaptor.forClass(studio.one.platform.ai.core.chat.ChatRequest.class);
        verify(chatPort, times(2)).chat(requests.capture());
        studio.one.platform.ai.core.chat.ChatRequest regenerationRequest = requests.getAllValues().get(1);
        JsonNode regenerationSchema = objectMapper.readTree(regenerationRequest.responseSchema());
        assertThat(regenerationSchema.path("properties").path("fields").path("properties")
                .properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("summary", "keywords");
        assertThat(regenerationRequest.messages().get(1).content())
                .startsWith("Allowed fields: [keywords, summary]");
        assertThat(regenerationRequest.messages().get(1).content())
                .contains("Output language: Korean (ko). Write summary prose and keywords in this language.");
        verify(repository).upsertResource(any(MarkdownResource.class));
    }

    @Test
    void requiredModePropagatesInsightEnrichmentFailure() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("pdf")
                .filename("required.pdf")
                .plainText("기본 정보만 있는 문서")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "기본 문서")
                        .id("block-1")
                        .sourceRef("page[1]/block[1]")
                        .order(0)
                        .build()))
                .metadata(Map.of(
                        "title", "기본 문서",
                        "keywords", List.of("기본 키워드")))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                properties.getMetadata());

        assertThatThrownBy(() -> service.preview(revision(), requiredOptions()))
                .isInstanceOf(MarkdownMetadataEnrichmentException.class)
                .satisfies(error -> assertThat(((MarkdownMetadataEnrichmentException) error).failure())
                        .isEqualTo(MarkdownMetadataEnrichmentException.Failure.MODEL_CONFIGURATION));
    }

    @Test
    void requiredModeClassifiesInvalidModelJsonSeparatelyFromUpstreamFailure() {
        DefaultMarkdownMetadataEnrichmentService service = serviceReturning("not-json");

        assertThatThrownBy(() -> service.preview(revision(), requiredOptions()))
                .isInstanceOf(MarkdownMetadataEnrichmentException.class)
                .satisfies(error -> assertThat(((MarkdownMetadataEnrichmentException) error).failure())
                        .isEqualTo(MarkdownMetadataEnrichmentException.Failure.INVALID_RESPONSE));
    }

    @Test
    void requiredModeRejectsAValidResponseThatOmitsKeywords() {
        DefaultMarkdownMetadataEnrichmentService service = serviceReturning("""
                {
                  "semanticType": "BOOK",
                  "subject": "HUMANITIES",
                  "confidence": 0.9,
                  "fields": {
                    "summary": {
                      "values": ["문서 요약이 필요한 본문을 정리한 요약"],
                      "evidenceText": "문서 요약이 필요한 본문"
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> service.preview(revision(), requiredOptions()))
                .isInstanceOf(MarkdownMetadataEnrichmentException.class)
                .satisfies(error -> assertThat(((MarkdownMetadataEnrichmentException) error).failure())
                        .isEqualTo(MarkdownMetadataEnrichmentException.Failure.INVALID_RESPONSE));
    }

    @Test
    void requiredModeExtractsTheFirstCompleteJsonObjectFromProviderText() {
        DefaultMarkdownMetadataEnrichmentService service = serviceReturning("""
                Structured result follows:
                {
                  "semanticType": "BOOK",
                  "subject": "HUMANITIES",
                  "confidence": 0.9,
                  "fields": {
                    "summary": {
                      "values": ["중괄호 {예시}를 포함한 문서 요약"],
                      "evidenceText": "문서 요약이 필요한 본문"
                    },
                    "keywords": {
                      "values": ["문서", "요약"],
                      "evidenceText": "문서 요약이 필요한 본문"
                    }
                  }
                }
                trailing provider text
                """);

        DocumentMetadataArtifact artifact = service.preview(revision(), requiredOptions());

        assertThat(artifact.fields()).containsKeys("summary", "keywords");
        assertThat(artifact.fields().get("summary").normalizedValues())
                .containsExactly("중괄호 {예시}를 포함한 문서 요약");
    }

    private DefaultMarkdownMetadataEnrichmentService serviceReturning(String response) {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("epub")
                .filename("invalid.epub")
                .plainText("문서 요약이 필요한 본문")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "요약 대상 문서")
                        .id("block-1")
                        .sourceRef("epub:chapter.xhtml#element[0]")
                        .order(0)
                        .build()))
                .metadata(Map.of("title", "요약 대상 문서"))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        ChatPort chatPort = request -> new ChatResponse(
                List.of(ChatMessage.assistant(response)), "test-model", Map.of());
        ModelDeploymentRegistry deployments = mock(ModelDeploymentRegistry.class);
        when(deployments.find("chat-default")).thenReturn(Optional.of(chatDeployment()));
        when(deployments.chatPort("chat-default")).thenReturn(chatPort);
        MarkdownProperties properties = new MarkdownProperties();
        return new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                deployments,
                properties.getMetadata());
    }

    private static MarkdownPipelineOptions autoOptions() {
        return new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null, null, null, null,
                null, null, null, null,
                false, null, false, null, null, null,
                null, null, null, null,
                "GENERAL_DOCUMENT", null, null, null,
                "AUTO", "AUTO");
    }

    private static MarkdownPipelineOptions offOptions() {
        return new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null, null, null, null,
                null, null, null, null,
                false, null, false, null, null, null,
                null, null, null, null,
                "GENERAL_DOCUMENT", null, null, null,
                "AUTO", "OFF");
    }

    private static MarkdownPipelineOptions requiredOptions() {
        return new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null, null, null, null,
                null, null, null, null,
                false, null, false, null, null, null,
                null, null, null, null,
                "GENERAL_DOCUMENT", null, null, null,
                "AUTO", "REQUIRED");
    }

    private static ModelDeployment chatDeployment() {
        ModelDefinition definition = new ModelDefinition(
                "test.chat",
                "Test Chat",
                "test",
                "openai",
                "gpt-test",
                java.util.Set.of(ModelWorkload.CHAT),
                java.util.Set.of(Modality.TEXT),
                java.util.Set.of(Modality.TEXT),
                java.util.Set.of(),
                ModelDimensionPolicy.none(),
                ModelLifecycle.STABLE,
                java.util.Set.of(),
                8192,
                2048,
                java.util.Set.of(),
                false,
                true,
                ModelDistribution.MANAGED_API,
                "test",
                java.util.List.of(),
                java.util.List.of(),
                java.util.Set.of(),
                ModelCatalogTier.ADAPTER_READY,
                "test",
                "https://example.com",
                null,
                "v1",
                null,
                "2026-08-14");
        return new ModelDeployment("chat-default", "openai", definition, ModelWorkload.CHAT, null, true);
    }

    private static MarkdownRevision revision() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new MarkdownRevision(
                "mrev-1", "mdoc-1", 9L, null, null,
                "TEXTRACT", "native", "{}", "options-hash", "source-hash", "content-hash",
                "인문학의 역사", "humanities.epub", "epub", "attachment", "9",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
    }
}
