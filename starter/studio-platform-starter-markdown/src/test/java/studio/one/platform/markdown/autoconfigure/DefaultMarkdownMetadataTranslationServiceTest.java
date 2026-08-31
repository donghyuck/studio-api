package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
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
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataClassification;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataProvenance;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.documentmetadata.DocumentSemanticTypeSelection;
import studio.one.platform.markdown.application.DocumentMetadataTranslationArtifact.GenerationMode;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownResource;

class DefaultMarkdownMetadataTranslationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void translatesToKoreanOnceAndReusesTheStoredSourceBoundArtifact() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownDocumentMetadataService metadataService = mock(MarkdownDocumentMetadataService.class);
        when(metadataService.get("mdoc-1", "mrev-1")).thenReturn(artifact(
                "en",
                "This book explains the history of artificial intelligence.",
                List.of("artificial intelligence", "history")));
        MarkdownRepository repository = mock(MarkdownRepository.class);
        AtomicReference<MarkdownResource> stored = new AtomicReference<>();
        when(repository.findResource("mrev-1", DefaultMarkdownMetadataTranslationService.RESOURCE_TYPE))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(repository).upsertResource(any(MarkdownResource.class));

        ChatPort chatPort = mock(ChatPort.class);
        when(chatPort.chat(any())).thenReturn(new ChatResponse(
                List.of(ChatMessage.assistant("""
                        {
                          "summary": "이 책은 인공지능의 역사를 설명합니다.",
                          "keywords": ["인공지능", "역사"]
                        }
                        """)),
                "gemini-test",
                Map.of()));
        ModelDeploymentRegistry deployments = mock(ModelDeploymentRegistry.class);
        when(deployments.find("chat-default")).thenReturn(Optional.of(chatDeployment()));
        when(deployments.chatPort("chat-default")).thenReturn(chatPort);
        DefaultMarkdownMetadataTranslationService service = new DefaultMarkdownMetadataTranslationService(
                metadataService,
                repository,
                objectMapper,
                deployments,
                new MarkdownProperties().getMetadata(),
                CLOCK);

        var created = service.translate("mdoc-1", "mrev-1", "ko");
        var reused = service.translate("mdoc-1", "mrev-1", "ko");

        assertThat(created.reused()).isFalse();
        assertThat(created.translation().summary()).isEqualTo("이 책은 인공지능의 역사를 설명합니다.");
        assertThat(created.translation().keywords()).containsExactly("인공지능", "역사");
        assertThat(created.translation().generationMode()).isEqualTo(GenerationMode.TRANSLATED);
        assertThat(reused.reused()).isTrue();
        assertThat(reused.translation()).isEqualTo(created.translation());
        assertThat(stored.get().resourceType())
                .isEqualTo(DefaultMarkdownMetadataTranslationService.RESOURCE_TYPE);
        verify(chatPort, times(1)).chat(any());
        verify(repository, times(1)).upsertResource(any(MarkdownResource.class));

        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatPort).chat(request.capture());
        assertThat(request.getValue().messages().get(1).content())
                .contains("This book explains the history of artificial intelligence.")
                .doesNotContain("NORMALIZED_DOCUMENT");
        assertThat(request.getValue().responseMimeType()).isEqualTo("application/json");
    }

    @Test
    void reusesKoreanSourceWithoutCallingAModel() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownDocumentMetadataService metadataService = mock(MarkdownDocumentMetadataService.class);
        when(metadataService.get("mdoc-1", "mrev-1")).thenReturn(artifact(
                "ko",
                "이 문서는 인공지능의 역사를 설명합니다.",
                List.of("인공지능", "역사")));
        MarkdownRepository repository = mock(MarkdownRepository.class);
        when(repository.findResource("mrev-1", DefaultMarkdownMetadataTranslationService.RESOURCE_TYPE))
                .thenReturn(Optional.empty());

        DefaultMarkdownMetadataTranslationService service = new DefaultMarkdownMetadataTranslationService(
                metadataService,
                repository,
                objectMapper,
                null,
                new MarkdownProperties().getMetadata(),
                CLOCK);

        var result = service.translate("mdoc-1", "mrev-1", "ko");

        assertThat(result.translation().generationMode()).isEqualTo(GenerationMode.SOURCE_REUSED);
        assertThat(result.translation().summary()).isEqualTo("이 문서는 인공지능의 역사를 설명합니다.");
        verify(repository).upsertResource(any(MarkdownResource.class));
    }

    @Test
    void regeneratesTranslationWhenTheCanonicalSummaryChanges() {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownDocumentMetadataService metadataService = mock(MarkdownDocumentMetadataService.class);
        when(metadataService.get("mdoc-1", "mrev-1")).thenReturn(
                artifact("en", "Original summary.", List.of("original")),
                artifact("en", "Revised canonical summary.", List.of("revised")));
        MarkdownRepository repository = mock(MarkdownRepository.class);
        AtomicReference<MarkdownResource> stored = new AtomicReference<>();
        when(repository.findResource("mrev-1", DefaultMarkdownMetadataTranslationService.RESOURCE_TYPE))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(repository).upsertResource(any(MarkdownResource.class));
        ChatPort chatPort = mock(ChatPort.class);
        when(chatPort.chat(any())).thenReturn(
                new ChatResponse(List.of(ChatMessage.assistant("""
                        {"summary":"원본 요약","keywords":["원본"]}
                        """)), "gemini-test", Map.of()),
                new ChatResponse(List.of(ChatMessage.assistant("""
                        {"summary":"수정된 요약","keywords":["수정"]}
                        """)), "gemini-test", Map.of()));
        ModelDeploymentRegistry deployments = mock(ModelDeploymentRegistry.class);
        when(deployments.find("chat-default")).thenReturn(Optional.of(chatDeployment()));
        when(deployments.chatPort("chat-default")).thenReturn(chatPort);
        DefaultMarkdownMetadataTranslationService service = new DefaultMarkdownMetadataTranslationService(
                metadataService,
                repository,
                objectMapper,
                deployments,
                new MarkdownProperties().getMetadata(),
                CLOCK);

        var first = service.translate("mdoc-1", "mrev-1", "ko");
        var second = service.translate("mdoc-1", "mrev-1", "ko");

        assertThat(first.reused()).isFalse();
        assertThat(second.reused()).isFalse();
        assertThat(second.translation().summary()).isEqualTo("수정된 요약");
        assertThat(second.translation().sourceSummaryHash())
                .isNotEqualTo(first.translation().sourceSummaryHash());
        verify(chatPort, times(2)).chat(any());
        verify(repository, times(2)).upsertResource(any(MarkdownResource.class));
    }

    private static DocumentMetadataArtifact artifact(String language, String summary, List<String> keywords) {
        return new DocumentMetadataArtifact(
                "metadata:mrev-1",
                "mrev-1",
                "document-metadata-v1",
                "extractor-v2",
                "source-fingerprint",
                new DocumentMetadataClassification(
                        null,
                        null,
                        DocumentSemanticTypeSelection.AUTO,
                        DocumentSemanticType.BOOK,
                        null,
                        "HUMANITIES",
                        0.9d,
                        "classifier-v1",
                        null,
                        null,
                        null,
                        null),
                DocumentMetadataQuality.COMPLETE,
                Map.of(
                        "language", field("language", List.of(language)),
                        "summary", field("summary", List.of(summary)),
                        "keywords", field("keywords", keywords)),
                List.of());
    }

    private static DocumentMetadataField field(String fieldId, List<String> values) {
        return new DocumentMetadataField(
                fieldId,
                values,
                values,
                0.9d,
                DocumentMetadataProvenance.INFERRED,
                List.of());
    }

    private static ModelDeployment chatDeployment() {
        ModelDefinition definition = new ModelDefinition(
                "test.chat",
                "Test Chat",
                "test",
                "google-ai",
                "gemini-test",
                Set.of(ModelWorkload.CHAT),
                Set.of(Modality.TEXT),
                Set.of(Modality.TEXT),
                Set.of(),
                ModelDimensionPolicy.none(),
                ModelLifecycle.STABLE,
                Set.of(),
                8192,
                2048,
                Set.of(),
                false,
                true,
                ModelDistribution.MANAGED_API,
                "test",
                List.of(),
                List.of(),
                Set.of(),
                ModelCatalogTier.ADAPTER_READY,
                "test",
                "https://example.com",
                null,
                "v1",
                null,
                "2026-08-20");
        return new ModelDeployment("chat-default", "google-ai", definition, ModelWorkload.CHAT, null, true);
    }
}
