package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.GoogleSpringAiChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.service.I18n;

class GoogleSpringAiChatRegistrationTest {

    @Test
    void registersGoogleChatAsSpringAiBackedPath() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("gemini-2.5-flash");
        provider.setApiKey("test-key");
        properties.getProviders().put("google", provider);

        LangChainChatConfiguration chatConfiguration = new LangChainChatConfiguration();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<I18n> i18nProvider = beanFactory.getBeanProvider(I18n.class);
        MockEnvironment environment = new MockEnvironment();

        Map<String, ChatPort> chatPorts = chatConfiguration.chatPorts(
                properties,
                i18nProvider,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class));

        assertThat(chatPorts).containsOnlyKeys("google");
        assertThat(chatPorts.get("google")).isInstanceOf(GoogleSpringAiChatAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("google", chatPorts, Map.of());
        assertThat(registry.defaultProvider()).isEqualTo("google");
        assertThat(registry.chatPort(null)).isSameAs(chatPorts.get("google"));
    }
}
