package studio.one.platform.ai.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.Provider;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties.ProviderType;

class AiSecretPresenceGuardTest {

    @Test
    void validateRejectsMissingApiKeyForOpenAiProvider() {
        AiAdapterProperties properties = new AiAdapterProperties();
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OPENAI);
        provider.setApiKey(" ");
        properties.getProviders().put("openai", provider);

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties);

        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void validateAllowsConfiguredOllamaBaseUrl() {
        AiAdapterProperties properties = new AiAdapterProperties();
        Provider provider = new Provider();
        provider.setEnabled(true);
        provider.setType(ProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        properties.getProviders().put("ollama", provider);

        AiSecretPresenceGuard guard = new AiSecretPresenceGuard(properties);

        assertDoesNotThrow(guard::validate);
    }
}
