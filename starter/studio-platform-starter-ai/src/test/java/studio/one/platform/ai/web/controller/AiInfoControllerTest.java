package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.web.dto.ApiResponse;

class AiInfoControllerTest {

    @Test
    void exposesCutoverDefaultProviderAndConfiguredProviders() {
        AiAdapterProperties properties = new AiAdapterProperties();
        properties.setDefaultProvider("openai-springai");

        AiAdapterProperties.Provider openAi = new AiAdapterProperties.Provider();
        openAi.setType(AiAdapterProperties.ProviderType.OPENAI);
        openAi.getChat().setEnabled(true);
        openAi.getEmbedding().setEnabled(true);
        properties.getProviders().put("openai", openAi);

        AiAdapterProperties.Provider google = new AiAdapterProperties.Provider();
        google.setType(AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI);
        google.getChat().setEnabled(true);
        properties.getProviders().put("google", google);

        AiInfoController controller = new AiInfoController(properties, null);

        ResponseEntity<ApiResponse<AiInfoController.AiInfoResponse>> response = controller.providers();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        AiInfoController.AiInfoResponse body = response.getBody().getData();
        assertThat(body.defaultProvider()).isEqualTo("openai-springai");
        assertThat(body.providers())
                .extracting(AiInfoController.ProviderInfo::name)
                .containsExactly("openai", "google");
        assertThat(body.vector().available()).isFalse();
    }
}
