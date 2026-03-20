package studio.one.platform.ai.autoconfigure;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;

@AutoConfiguration
@EnableConfigurationProperties(AiAdapterProperties.class)
@ConditionalOnProperty(prefix = "studio.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AiSecretPresenceGuard {

    private final AiAdapterProperties properties;

    @PostConstruct
    void validate() {
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            AiAdapterProperties.Provider provider = entry.getValue();
            if (provider == null || !provider.isEnabled() || provider.getType() == null) {
                continue;
            }
            switch (provider.getType()) {
                case OPENAI, GOOGLE_AI_GEMINI -> requireText(provider.getApiKey(),
                        "studio.ai.providers." + providerId + ".api-key must be configured");
                case OLLAMA -> requireText(provider.getBaseUrl(),
                        "studio.ai.providers." + providerId + ".base-url must be configured");
                default -> {
                }
            }
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
