package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import studio.one.platform.ai.autoconfigure.AiWebChatProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.web.dto.ApiResponse;

class AiInfoControllerTest {

    @Test
    void exposesSpringAiProviderModelsAsCanonicalSource() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google-ai-gemini");

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        google.getChat().setModel("legacy-google-chat");
        google.getEmbedding().setEnabled(true);
        google.getEmbedding().setModel("legacy-google-embedding");
        properties.getProviders().put("google-ai-gemini", google);

        AiAdapterProperties.Provider ollama = new AiAdapterProperties.Provider();
        ollama.setType(AiAdapterProperties.ProviderType.OLLAMA);
        ollama.getEmbedding().setEnabled(true);
        ollama.getEmbedding().setModel("legacy-ollama-embedding");
        properties.getProviders().put("ollama", ollama);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.chat.options.model", "gemini-2.5-flash")
                .withProperty("spring.ai.google.genai.embedding.text.options.model", "gemini-embedding-001")
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");
        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                environment,
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().providers()).hasSize(2);
        AiInfoController.ProviderInfo googleInfo = body.getData().providers().get(0);
        assertThat(googleInfo.name()).isEqualTo("google-ai-gemini");
        assertThat(googleInfo.chat().model()).isEqualTo("gemini-2.5-flash");
        assertThat(googleInfo.embedding().model()).isEqualTo("gemini-embedding-001");
        AiInfoController.ProviderInfo ollamaInfo = body.getData().providers().get(1);
        assertThat(ollamaInfo.embedding().model()).isEqualTo("nomic-embed-text");
    }

    @Test
    void exposesOpenAiBaseUrlOnlyFromSpringAiCanonicalProperty() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai");

        AiAdapterProperties.Provider openai = new AiAdapterProperties.Provider();
        openai.setType(AiAdapterProperties.ProviderType.OPENAI);
        openai.setBaseUrl("https://legacy.example.invalid");
        properties.getProviders().put("openai", openai);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().providers()).hasSize(1);
        assertThat(body.getData().providers().get(0).baseUrl()).isNull();
    }

    @Test
    void skipsProvidersWithoutType() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("google-ai-gemini");

        AiAdapterProperties.Provider incomplete = new AiAdapterProperties.Provider();
        properties.getProviders().put("incomplete", incomplete);

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        properties.getProviders().put("google-ai-gemini", google);

        AiInfoController controller = new AiInfoController(
                properties,
                new AiWebChatProperties(),
                new MockEnvironment(),
                null);

        ApiResponse<AiInfoController.AiInfoResponse> body = controller.providers().getBody();

        assertThat(body.getData().providers())
                .extracting(AiInfoController.ProviderInfo::name)
                .containsExactly("google-ai-gemini");
    }
}
