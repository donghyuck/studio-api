package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.adapter.GoogleSpringAiChatAdapter;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

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

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment();

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        assertThat(chatPorts).containsOnlyKeys("google");
        assertThat(chatPorts.get("google")).isInstanceOf(GoogleSpringAiChatAdapter.class);

        AiProviderRegistry registry = new AiProviderRegistry("google", chatPorts, Map.of());
        assertThat(registry.defaultProvider()).isEqualTo("google");
        assertThat(registry.chatPort(null)).isSameAs(chatPorts.get("google"));
    }

    @Test
    void prefersInjectedSpringAiChatModelWithoutLegacyClientProperties() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        properties.getProviders().put("google", provider);

        org.springframework.ai.chat.model.ChatModel injected =
                org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("googleChatModel", injected);

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                new MockEnvironment(),
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        GoogleSpringAiChatAdapter adapter = (GoogleSpringAiChatAdapter) chatPorts.get("google");
        Field chatModelField = studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter.class
                .getDeclaredField("chatModel");
        chatModelField.setAccessible(true);

        assertThat(chatModelField.get(adapter)).isSameAs(injected);
    }

    @Test
    void preservesConfiguredBaseUrlForGoogleChat() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("gemini-2.5-flash");
        provider.setApiKey("test-key");
        provider.setBaseUrl("https://proxy.example.test/v1beta");
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment();

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        GoogleSpringAiChatAdapter adapter = (GoogleSpringAiChatAdapter) chatPorts.get("google");
        Field chatModelField = studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter.class
                .getDeclaredField("chatModel");
        chatModelField.setAccessible(true);
        Object chatModel = chatModelField.get(adapter);

        Field clientField = chatModel.getClass().getDeclaredField("genAiClient");
        clientField.setAccessible(true);
        Object client = clientField.get(chatModel);

        Method baseUrlMethod = client.getClass().getDeclaredMethod("baseUrl");
        baseUrlMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Optional<String> baseUrl = (java.util.Optional<String>) baseUrlMethod.invoke(client);

        assertThat(baseUrl).contains("https://proxy.example.test/v1beta");
    }

    @Test
    void prefersSpringAiGoogleChatPropertiesOverLegacyStudioProviderFields() throws Exception {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google");

        AiAdapterProperties.Provider provider = new AiAdapterProperties.Provider();
        provider.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        provider.getChat().setEnabled(true);
        provider.getChat().setModel("legacy-google-chat");
        provider.setApiKey("legacy-key");
        properties.getProviders().put("google", provider);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.api-key", "spring-key")
                .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash");

        Map<String, ChatPort> chatPorts = new ProviderChatConfiguration().chatPorts(
                properties,
                environment,
                beanFactory.getBeanProvider(org.springframework.ai.chat.model.ChatModel.class),
                List.of(new GoogleGenAiChatPortFactoryConfiguration().googleGenAiChatPortFactory()));

        GoogleSpringAiChatAdapter adapter = (GoogleSpringAiChatAdapter) chatPorts.get("google");
        Field chatModelField = studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapter.class
                .getDeclaredField("chatModel");
        chatModelField.setAccessible(true);
        Object chatModel = chatModelField.get(adapter);

        Field defaultOptionsField = chatModel.getClass().getDeclaredField("defaultOptions");
        defaultOptionsField.setAccessible(true);
        Object defaultOptions = defaultOptionsField.get(chatModel);

        Method modelMethod = defaultOptions.getClass().getMethod("getModel");
        assertThat(modelMethod.invoke(defaultOptions)).isEqualTo("gemini-2.5-flash");
    }
}
