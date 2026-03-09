package studio.one.platform.storage.autoconfigure;

import java.util.Locale;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@RequiredArgsConstructor
public class ObjectStorageSecretPresenceGuard {

    private final StorageProperties properties;

    @PostConstruct
    void validate() {
        for (Map.Entry<String, StorageProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            StorageProperties.Provider provider = entry.getValue();
            if (provider == null || !provider.isEnabled()) {
                continue;
            }
            String type = provider.getType() == null ? "" : provider.getType().trim().toLowerCase(Locale.ROOT);
            switch (type) {
                case "s3" -> {
                    requireText(provider.getCredentials().getAccessKey(),
                            "studio.cloud.storage.providers." + providerId + ".credentials.access-key must be configured");
                    requireText(provider.getCredentials().getSecretKey(),
                            "studio.cloud.storage.providers." + providerId + ".credentials.secret-key must be configured");
                }
                case "fs" -> requireText(provider.getFs().getRoot(),
                        "studio.cloud.storage.providers." + providerId + ".fs.root must be configured");
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
