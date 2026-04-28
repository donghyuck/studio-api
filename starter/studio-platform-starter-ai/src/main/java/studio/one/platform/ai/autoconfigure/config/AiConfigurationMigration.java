package studio.one.platform.ai.autoconfigure.config;

import org.slf4j.Logger;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.constant.PropertyKeys;

public final class AiConfigurationMigration {

    public static final String FEATURE_ENABLED_KEY = "studio.features.ai.enabled";
    public static final String LEGACY_FEATURE_ENABLED_KEY = PropertyKeys.AI.PREFIX + ".enabled";
    public static final String ROUTING_PREFIX = PropertyKeys.AI.PREFIX + ".routing";
    public static final String RAG_PREFIX = PropertyKeys.AI.PREFIX + ".rag";
    public static final String LEGACY_RAG_PREFIX = PropertyKeys.AI.PREFIX + ".pipeline";

    private static final String DEFAULT_CHAT_PROVIDER_KEY = ROUTING_PREFIX + ".default-chat-provider";
    private static final String DEFAULT_EMBEDDING_PROVIDER_KEY = ROUTING_PREFIX + ".default-embedding-provider";
    private static final String LEGACY_DEFAULT_PROVIDER_KEY = PropertyKeys.AI.PREFIX + ".default-provider";
    private static final String LEGACY_DEFAULT_CHAT_PROVIDER_KEY = PropertyKeys.AI.PREFIX + ".default-chat-provider";
    private static final String LEGACY_DEFAULT_EMBEDDING_PROVIDER_KEY =
            PropertyKeys.AI.PREFIX + ".default-embedding-provider";
    private static final String MIGRATION_REASON =
            "AI configuration namespace migration for issue #354.";

    private AiConfigurationMigration() {
    }

    public static boolean isAiFeatureEnabled(Environment environment, Logger log) {
        return ConfigurationPropertyMigration.getBooleanWithLegacyFallback(
                environment,
                FEATURE_ENABLED_KEY,
                LEGACY_FEATURE_ENABLED_KEY,
                false,
                log,
                MIGRATION_REASON);
    }

    public static RoutingDefaults resolveRouting(
            AiAdapterProperties properties,
            Environment environment,
            Logger log) {
        String legacyDefaultProvider = normalize(properties.getDefaultProvider());
        String defaultChatProvider = targetOrLegacy(
                environment,
                DEFAULT_CHAT_PROVIDER_KEY,
                properties.getRouting().getDefaultChatProvider(),
                LEGACY_DEFAULT_CHAT_PROVIDER_KEY,
                properties.getDefaultChatProvider(),
                log);
        String defaultEmbeddingProvider = targetOrLegacy(
                environment,
                DEFAULT_EMBEDDING_PROVIDER_KEY,
                properties.getRouting().getDefaultEmbeddingProvider(),
                LEGACY_DEFAULT_EMBEDDING_PROVIDER_KEY,
                properties.getDefaultEmbeddingProvider(),
                log);
        if (defaultChatProvider == null && legacyDefaultProvider != null) {
            warn(log, LEGACY_DEFAULT_PROVIDER_KEY, DEFAULT_CHAT_PROVIDER_KEY);
            defaultChatProvider = legacyDefaultProvider;
        }
        if (defaultEmbeddingProvider == null && legacyDefaultProvider != null) {
            warn(log, LEGACY_DEFAULT_PROVIDER_KEY, DEFAULT_EMBEDDING_PROVIDER_KEY);
            defaultEmbeddingProvider = legacyDefaultProvider;
        }
        String defaultProvider = defaultChatProvider;
        return new RoutingDefaults(defaultProvider, defaultChatProvider, defaultEmbeddingProvider,
                legacyDefaultProvider != null);
    }

    public static String springOrLegacyProviderValue(
            Environment environment,
            String springKey,
            String springValue,
            String legacyKey,
            String legacyValue,
            Logger log) {
        String normalizedSpringValue = trimToNull(springValue);
        if (normalizedSpringValue != null) {
            return normalizedSpringValue;
        }
        String environmentValue = trimToNull(environment.getProperty(springKey));
        if (environmentValue != null) {
            return environmentValue;
        }
        String normalizedLegacyValue = trimToNull(legacyValue);
        if (normalizedLegacyValue != null) {
            warn(log, legacyKey, springKey);
            return normalizedLegacyValue;
        }
        return null;
    }

    public static String springOrLegacyProviderValue(
            Environment environment,
            String springKey,
            String legacyKey,
            String legacyValue,
            Logger log) {
        return springOrLegacyProviderValue(environment, springKey, null, legacyKey, legacyValue, log);
    }

    public static void applyRagPipelineFallback(Environment environment, RagPipelineProperties properties, Logger log) {
        if (environment == null || properties == null || hasRagPipelineTargetProperties(environment)
                || !hasLegacyRagPipelineProperties(environment)) {
            return;
        }
        Binder.get(environment).bind(LEGACY_RAG_PREFIX, Bindable.ofInstance(properties));
        warn(log, LEGACY_RAG_PREFIX + ".*", RAG_PREFIX + ".*");
    }

    public static String propertyWithLegacyFallback(
            Environment environment,
            String targetKey,
            String legacyKey,
            String defaultValue,
            Logger log) {
        String targetValue = trimToNull(environment.getProperty(targetKey));
        if (targetValue != null) {
            return targetValue;
        }
        String legacyValue = trimToNull(environment.getProperty(legacyKey));
        if (legacyValue != null) {
            warn(log, legacyKey, targetKey);
            return legacyValue;
        }
        return defaultValue;
    }

    public static boolean booleanWithLegacyFallback(
            Environment environment,
            String targetKey,
            String legacyKey,
            boolean defaultValue,
            Logger log) {
        return ConfigurationPropertyMigration.getBooleanWithLegacyFallback(
                environment,
                targetKey,
                legacyKey,
                defaultValue,
                log,
                MIGRATION_REASON);
    }

    private static String targetOrLegacy(
            Environment environment,
            String targetKey,
            String targetValue,
            String legacyKey,
            String legacyValue,
            Logger log) {
        String normalizedTarget = normalize(targetValue);
        if (normalizedTarget != null) {
            return normalizedTarget;
        }
        if (environment != null) {
            String environmentTarget = normalize(environment.getProperty(targetKey));
            if (environmentTarget != null) {
                return environmentTarget;
            }
        }
        String normalizedLegacy = normalize(legacyValue);
        if (normalizedLegacy != null) {
            warn(log, legacyKey, targetKey);
            return normalizedLegacy;
        }
        return null;
    }

    private static boolean hasRagPipelineTargetProperties(Environment environment) {
        return hasProperty(environment, RAG_PREFIX + ".chunk-size")
                || hasProperty(environment, RAG_PREFIX + ".chunk-overlap")
                || hasPropertyGroup(environment, RAG_PREFIX + ".cache")
                || hasPropertyGroup(environment, RAG_PREFIX + ".retry")
                || hasPropertyGroup(environment, RAG_PREFIX + ".retrieval")
                || hasPropertyGroup(environment, RAG_PREFIX + ".object-scope")
                || hasPropertyGroup(environment, RAG_PREFIX + ".cleaner")
                || hasPropertyGroup(environment, RAG_PREFIX + ".diagnostics")
                || hasPropertyGroup(environment, RAG_PREFIX + ".keywords")
                || hasPropertyGroup(environment, RAG_PREFIX + ".jobs");
    }

    private static boolean hasLegacyRagPipelineProperties(Environment environment) {
        return hasProperty(environment, LEGACY_RAG_PREFIX + ".chunk-size")
                || hasProperty(environment, LEGACY_RAG_PREFIX + ".chunk-overlap")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".cache")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".retry")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".retrieval")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".object-scope")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".cleaner")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".diagnostics")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".keywords")
                || hasPropertyGroup(environment, LEGACY_RAG_PREFIX + ".jobs");
    }

    private static boolean hasProperty(Environment environment, String key) {
        return StringUtils.hasText(environment.getProperty(key));
    }

    private static boolean hasPropertyGroup(Environment environment, String prefix) {
        return ConfigurationPropertyMigration.hasProperties(environment, prefix);
    }

    private static void warn(Logger log, String legacyKey, String replacementKey) {
        if (log == null) {
            return;
        }
        ConfigurationPropertyMigration.warnDeprecated(log, legacyKey, replacementKey, MIGRATION_REASON);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RoutingDefaults(
            String defaultProvider,
            String defaultChatProvider,
            String defaultEmbeddingProvider,
            boolean legacyDefaultProviderConfigured) {
    }
}
