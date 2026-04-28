package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

@ExtendWith(OutputCaptureExtension.class)
class AiConfigurationMigrationTest {

    @BeforeEach
    void resetWarnings() throws Exception {
        Field warned = ConfigurationPropertyMigration.class.getDeclaredField("WARNED");
        warned.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> values = (Set<String>) warned.get(null);
        values.clear();
    }

    @Test
    void featureGateUsesTargetBeforeLegacy(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.features.ai.enabled", "false")
                .withProperty("studio.ai.enabled", "true");

        boolean enabled = AiConfigurationMigration.isAiFeatureEnabled(
                environment,
                LoggerFactory.getLogger(getClass()));

        assertThat(enabled).isFalse();
        assertThat(output).doesNotContain("[DEPRECATED CONFIG]");
    }

    @Test
    void featureGateUsesLegacyFallbackWithWarning(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.ai.enabled", "true");

        boolean enabled = AiConfigurationMigration.isAiFeatureEnabled(
                environment,
                LoggerFactory.getLogger(getClass()));

        assertThat(enabled).isTrue();
        assertThat(output)
                .contains("[DEPRECATED CONFIG] studio.ai.enabled is deprecated")
                .contains("Use studio.features.ai.enabled instead");
    }

    @Test
    void routingTargetKeysWinOverLegacyDefaults() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("legacy");
        properties.setDefaultChatProvider("legacy-chat");
        properties.setDefaultEmbeddingProvider("legacy-embedding");
        properties.getRouting().setDefaultChatProvider("chat-target");
        properties.getRouting().setDefaultEmbeddingProvider("embedding-target");

        AiConfigurationMigration.RoutingDefaults routing = AiConfigurationMigration.resolveRouting(
                properties,
                new MockEnvironment(),
                null);

        assertThat(routing.defaultProvider()).isEqualTo("chat-target");
        assertThat(routing.defaultChatProvider()).isEqualTo("chat-target");
        assertThat(routing.defaultEmbeddingProvider()).isEqualTo("embedding-target");
    }

    @Test
    void routingLegacyDefaultProviderFillsBothTargetsWithWarning(CapturedOutput output) {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiConfigurationMigration.RoutingDefaults routing = AiConfigurationMigration.resolveRouting(
                properties,
                new MockEnvironment(),
                LoggerFactory.getLogger(getClass()));

        assertThat(routing.defaultProvider()).isEqualTo("openai");
        assertThat(routing.defaultChatProvider()).isEqualTo("openai");
        assertThat(routing.defaultEmbeddingProvider()).isEqualTo("openai");
        assertThat(output)
                .contains("studio.ai.default-provider is deprecated")
                .contains("studio.ai.routing.default-chat-provider")
                .contains("studio.ai.routing.default-embedding-provider");
    }

    @Test
    void warnsWhenLegacyProviderClientKeysAreUsedAsFallback(CapturedOutput output) {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.setApiKey("legacy-key");
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("gemini-legacy");
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                new MockEnvironment(),
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                java.util.List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        assertThat(chatPorts).containsOnlyKeys("google");
        assertThat(output)
                .contains("studio.ai.providers.google.api-key is deprecated")
                .contains("studio.ai.providers.google.chat.model is deprecated");
    }

    @Test
    void skipsDisabledProviderWithoutSpringAiBeanOrLegacyClientProperties() {
        AiAdapterProperties properties = new AiAdapterProperties();
        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(false);
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                new MockEnvironment(),
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                java.util.List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        assertThat(chatPorts).isEmpty();
    }
}
