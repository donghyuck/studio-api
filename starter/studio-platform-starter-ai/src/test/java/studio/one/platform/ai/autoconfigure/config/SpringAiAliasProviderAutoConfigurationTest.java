package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.ai.autoconfigure.AiSecretPresenceGuard;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.service.I18n;

class SpringAiAliasProviderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    AiSecretPresenceGuard.class,
                    LangChainChatConfiguration.class,
                    LangChainEmbeddingConfiguration.class,
                    AiProviderRegistryConfiguration.class)
            .withBean(I18n.class, () -> (code, args, locale) -> code)
            .withBean(org.springframework.ai.chat.model.ChatModel.class,
                    () -> org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class))
            .withBean(org.springframework.ai.embedding.EmbeddingModel.class,
                    () -> org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class))
            .withPropertyValues(
                    "studio.ai.enabled=true",
                    "studio.ai.default-provider=openai-springai",
                    "studio.ai.spring-ai.source-provider=openai",
                    "studio.ai.spring-ai.enabled=true",
                    "studio.ai.spring-ai.provider-suffix=-springai",
                    "spring.ai.openai.api-key=test-key",
                    "studio.ai.providers.openai.type=OPENAI",
                    "studio.ai.providers.openai.api-key=test-key",
                    "studio.ai.providers.openai.chat.enabled=true",
                    "studio.ai.providers.openai.chat.model=gpt-4o-mini",
                    "studio.ai.providers.openai.embedding.enabled=true",
                    "studio.ai.providers.openai.embedding.model=text-embedding-3-small");

    @Test
    void usesSpringAiAliasAsDefaultProviderWhenConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiProviderRegistry.class);
            assertThat(context).hasSingleBean(ChatPort.class);
            assertThat(context).hasSingleBean(EmbeddingPort.class);

            AiProviderRegistry registry = context.getBean(AiProviderRegistry.class);
            ChatPort defaultChatPort = context.getBean(ChatPort.class);
            EmbeddingPort defaultEmbeddingPort = context.getBean(EmbeddingPort.class);

            assertThat(registry.defaultProvider()).isEqualTo("openai-springai");
            assertThat(registry.availableChatPorts()).containsKeys("openai", "openai-springai");
            assertThat(registry.availableEmbeddingPorts()).containsKeys("openai", "openai-springai");
            assertThat(defaultChatPort).isSameAs(registry.chatPort(null));
            assertThat(defaultEmbeddingPort).isSameAs(registry.embeddingPort(null));
            assertThat(defaultChatPort).isSameAs(registry.chatPort("openai-springai"));
            assertThat(defaultEmbeddingPort).isSameAs(registry.embeddingPort("openai-springai"));
        });
    }

    @Test
    void doesNotRegisterSpringAiAliasForNonOpenAiProviders() {
        contextRunner
                .withPropertyValues(
                        "studio.ai.providers.google.type=GOOGLE_AI_GEMINI",
                        "studio.ai.providers.google.api-key=test-key",
                        "studio.ai.providers.google.chat.enabled=true",
                        "studio.ai.providers.google.chat.model=gemini-1.5-flash",
                        "studio.ai.providers.google.embedding.enabled=true",
                        "studio.ai.providers.google.embedding.model=text-embedding-004")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiProviderRegistry registry = context.getBean(AiProviderRegistry.class);
                    assertThat(registry.availableChatPorts()).containsKeys("openai", "openai-springai", "google");
                    assertThat(registry.availableChatPorts()).doesNotContainKey("google-springai");
                    assertThat(registry.availableEmbeddingPorts()).containsKeys("openai", "openai-springai", "google");
                    assertThat(registry.availableEmbeddingPorts()).doesNotContainKey("google-springai");
                });
    }

    @Test
    void failsWhenDefaultSpringAiAliasHasNoChatPort() {
        contextRunner
                .withPropertyValues("studio.ai.providers.openai.chat.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("Unknown provider: openai-springai");
                });
    }

    @Test
    void failsWhenDefaultSpringAiAliasHasNoEmbeddingPort() {
        contextRunner
                .withPropertyValues("studio.ai.providers.openai.embedding.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("Unknown provider: openai-springai");
                });
    }

    @Test
    void failsFastWhenSpringAiAliasIsEnabledWithoutSpringAiApiKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(
                        AiSecretPresenceGuard.class,
                        LangChainChatConfiguration.class,
                        LangChainEmbeddingConfiguration.class,
                        AiProviderRegistryConfiguration.class)
                .withBean(I18n.class, () -> (code, args, locale) -> code)
                .withBean(org.springframework.ai.chat.model.ChatModel.class,
                        () -> org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class))
                .withBean(org.springframework.ai.embedding.EmbeddingModel.class,
                        () -> org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class))
                .withPropertyValues(
                        "studio.ai.enabled=true",
                        "studio.ai.default-provider=openai-springai",
                        "studio.ai.spring-ai.source-provider=openai",
                        "studio.ai.spring-ai.enabled=true",
                        "studio.ai.spring-ai.provider-suffix=-springai",
                        "studio.ai.providers.openai.type=OPENAI",
                        "studio.ai.providers.openai.api-key=test-key",
                        "studio.ai.providers.openai.chat.enabled=true",
                        "studio.ai.providers.openai.chat.model=gpt-4o-mini",
                        "studio.ai.providers.openai.embedding.enabled=true",
                        "studio.ai.providers.openai.embedding.model=text-embedding-3-small")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("spring.ai.openai.api-key must be configured when studio.ai.spring-ai.enabled=true");
                });
    }
}
