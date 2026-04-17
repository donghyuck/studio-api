package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.Embedding;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;

import studio.one.platform.ai.autoconfigure.AiWebAutoConfiguration;
import studio.one.platform.ai.autoconfigure.AiSecretPresenceGuard;
import studio.one.platform.ai.core.chat.ChatMemoryStore;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.controller.AiInfoController;
import studio.one.platform.ai.web.controller.ChatController;
import studio.one.platform.ai.web.controller.EmbeddingController;
import studio.one.platform.ai.web.controller.QueryRewriteController;
import studio.one.platform.ai.web.controller.RagController;
import studio.one.platform.ai.web.controller.VectorController;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.ChatResponseDto;
import studio.one.platform.ai.web.dto.EmbeddingRequestDto;
import studio.one.platform.ai.web.dto.EmbeddingResponseDto;
import studio.one.platform.service.I18n;
import studio.one.platform.web.dto.ApiResponse;

class OpenAiProviderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    AiSecretPresenceGuard.class,
                    AiWebAutoConfiguration.class,
                    OpenAiPortFactoryConfiguration.class,
                    ProviderChatConfiguration.class,
                    ProviderEmbeddingConfiguration.class,
                    AiProviderRegistryConfiguration.class)
            .withBean(I18n.class, () -> (code, args, locale) -> code)
            .withBean(RagPipelineService.class, () -> org.mockito.Mockito.mock(RagPipelineService.class))
            .withBean(PromptRenderer.class, () -> org.mockito.Mockito.mock(PromptRenderer.class))
            .withBean(org.springframework.ai.chat.model.ChatModel.class,
                    () -> org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class))
            .withBean(org.springframework.ai.embedding.EmbeddingModel.class,
                    () -> org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class))
            .withPropertyValues(
                    "studio.ai.enabled=true",
                    "studio.ai.endpoints.enabled=true",
                    "studio.ai.default-provider=openai",
                    "spring.ai.openai.api-key=test-key",
                    "spring.ai.openai.chat.options.model=gpt-4o-mini",
                    "spring.ai.openai.embedding.options.model=text-embedding-3-small",
                    "studio.ai.providers.openai.type=OPENAI",
                    "studio.ai.providers.openai.chat.enabled=true",
                    "studio.ai.providers.openai.embedding.enabled=true");

    @Test
    void usesOpenAiAsDefaultProviderWhenConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiProviderRegistry.class);
            assertThat(context).hasSingleBean(ChatPort.class);
            assertThat(context).hasSingleBean(EmbeddingPort.class);
            assertThat(context).hasSingleBean(ChatController.class);
            assertThat(context).hasSingleBean(EmbeddingController.class);
            assertThat(context).hasSingleBean(AiInfoController.class);
            assertThat(context).doesNotHaveBean(ChatMemoryStore.class);

            AiProviderRegistry registry = context.getBean(AiProviderRegistry.class);
            assertThat(registry.defaultProvider()).isEqualTo("openai");
            assertThat(registry.availableChatPorts()).containsOnlyKeys("openai");
            assertThat(registry.availableEmbeddingPorts()).containsOnlyKeys("openai");
            assertThat(context.getBean(ChatPort.class)).isSameAs(registry.chatPort("openai"));
            assertThat(context.getBean(EmbeddingPort.class)).isSameAs(registry.embeddingPort("openai"));
        });
    }

    @Test
    void routesControllerChatRequestsThroughDefaultOpenAiProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChatController.class);

            org.springframework.ai.chat.model.ChatModel springAiChatModel =
                    context.getBean(org.springframework.ai.chat.model.ChatModel.class);
            when(springAiChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                    .thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                            List.of(new Generation(
                                    new AssistantMessage("hello from spring ai"),
                                    ChatGenerationMetadata.builder().finishReason("stop").build())),
                            ChatResponseMetadata.builder().id("resp-1").model("gpt-4.1-mini").build()));

            ChatController controller = context.getBean(ChatController.class);

            ResponseEntity<ApiResponse<ChatResponseDto>> response = controller.chat(new ChatRequestDto(
                    null,
                    null,
                    List.of(new ChatMessageDto("user", "hello")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null), null);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().getData().model()).isEqualTo("gpt-4.1-mini");
            assertThat(response.getBody().getData().messages())
                    .extracting(ChatMessageDto::content)
                    .containsExactly("hello from spring ai");
        });
    }

    @Test
    void routesControllerEmbeddingRequestsThroughDefaultOpenAiProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(EmbeddingController.class);

            org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel =
                    context.getBean(org.springframework.ai.embedding.EmbeddingModel.class);
            when(springAiEmbeddingModel.embedForResponse(anyList()))
                    .thenReturn(new org.springframework.ai.embedding.EmbeddingResponse(List.of(
                            new Embedding(new float[] { 1.0f, 2.0f }, 0))));

            EmbeddingController controller = context.getBean(EmbeddingController.class);

            ResponseEntity<ApiResponse<EmbeddingResponseDto>> response =
                    controller.embed(new EmbeddingRequestDto(List.of("first")));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().getData().vectors()).hasSize(1);
            assertThat(response.getBody().getData().vectors().get(0).referenceId()).isEqualTo("first");
            assertThat(response.getBody().getData().vectors().get(0).values()).containsExactly(1.0, 2.0);
        });
    }

    @Test
    void registersInMemoryChatMemoryStoreWhenChatMemoryIsEnabled() {
        contextRunner
                .withPropertyValues("studio.ai.endpoints.chat.memory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatMemoryStore.class);
                    assertThat(context).hasSingleBean(ChatController.class);
                });
    }

    @Test
    void exposesOpenAiFromInfoControllerInRuntimeContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiInfoController.class);

            AiInfoController controller = context.getBean(AiInfoController.class);

            ResponseEntity<ApiResponse<AiInfoController.AiInfoResponse>> response = controller.providers();

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody().getData().defaultProvider()).isEqualTo("openai");
            assertThat(response.getBody().getData().providers())
                    .extracting(AiInfoController.ProviderInfo::name)
                    .containsExactly("openai");
            assertThat(response.getBody().getData().chat().memory().enabled()).isFalse();
        });
    }

    @Test
    void failsFastWhenOpenAiApiKeyIsMissing() {
        contextRunner
                .withPropertyValues("spring.ai.openai.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("spring.ai.openai.api-key must be configured for OPENAI provider");
                });
    }

    @Test
    void failsFastWhenMultipleOpenAiProvidersAreEnabled() {
        contextRunner
                .withPropertyValues(
                        "studio.ai.providers.backup-openai.type=OPENAI",
                        "studio.ai.providers.backup-openai.chat.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Exactly one enabled OPENAI provider is supported");
                });
    }

    @Test
    void doesNotRegisterWebControllersWhenEndpointsAreDisabled() {
        contextRunner
                .withPropertyValues("studio.ai.endpoints.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatController.class);
                    assertThat(context).doesNotHaveBean(EmbeddingController.class);
                    assertThat(context).doesNotHaveBean(AiInfoController.class);
                    assertThat(context).doesNotHaveBean(VectorController.class);
                    assertThat(context).doesNotHaveBean(RagController.class);
                    assertThat(context).doesNotHaveBean(QueryRewriteController.class);
                });
    }

    @Test
    void doesNotRegisterWebControllersWhenAiIsDisabled() {
        contextRunner
                .withPropertyValues("studio.ai.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatController.class);
                    assertThat(context).doesNotHaveBean(EmbeddingController.class);
                    assertThat(context).doesNotHaveBean(AiInfoController.class);
                    assertThat(context).doesNotHaveBean(VectorController.class);
                    assertThat(context).doesNotHaveBean(RagController.class);
                    assertThat(context).doesNotHaveBean(QueryRewriteController.class);
                });
    }
}
