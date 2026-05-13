package studio.one.platform.ai.autoconfigure.config;

import org.slf4j.Logger;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.constant.PropertyKeys;

import java.time.Duration;
import java.util.function.Consumer;

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

    public static String studioOrSpringProviderValue(
            Environment environment,
            String studioKey,
            String studioValue,
            String springKey,
            Logger log) {
        String normalizedStudioValue = trimToNull(studioValue);
        if (normalizedStudioValue != null) {
            return normalizedStudioValue;
        }
        String environmentStudioValue = environment == null ? null : trimToNull(environment.getProperty(studioKey));
        if (environmentStudioValue != null) {
            return environmentStudioValue;
        }
        String springValue = environment == null ? null : trimToNull(environment.getProperty(springKey));
        if (springValue != null) {
            warn(log, springKey, studioKey);
            return springValue;
        }
        return null;
    }

    public static String studioOrSpringProviderValue(
            Environment environment,
            String studioKey,
            String springKey,
            Logger log) {
        return studioOrSpringProviderValue(environment, studioKey, null, springKey, log);
    }

    public static void applyRagPipelineFallback(Environment environment, RagPipelineProperties properties, Logger log) {
        if (environment == null || properties == null) {
            return;
        }
        bindLegacyRagLeaf(environment, "chunk-size", Integer.class, properties::setChunkSize, log);
        bindLegacyRagLeaf(environment, "chunk-overlap", Integer.class, properties::setChunkOverlap, log);
        bindLegacyRagLeaf(environment, "cache.maximum-size", Long.class, properties.getCache()::setMaximumSize, log);
        bindLegacyRagLeaf(environment, "cache.ttl", Duration.class, properties.getCache()::setTtl, log);
        bindLegacyRagLeaf(environment, "retry.max-attempts", Integer.class, properties.getRetry()::setMaxAttempts, log);
        bindLegacyRagLeaf(environment, "retry.wait-duration", Duration.class, properties.getRetry()::setWaitDuration, log);
        bindLegacyRagLeaf(environment, "retrieval.vector-weight", Double.class,
                properties.getRetrieval()::setVectorWeight, log);
        bindLegacyRagLeaf(environment, "retrieval.lexical-weight", Double.class,
                properties.getRetrieval()::setLexicalWeight, log);
        bindLegacyRagLeaf(environment, "retrieval.min-relevance-score", Double.class,
                properties.getRetrieval()::setMinRelevanceScore, log);
        bindLegacyRagLeaf(environment, "retrieval.keyword-fallback-enabled", Boolean.class,
                properties.getRetrieval()::setKeywordFallbackEnabled, log);
        bindLegacyRagLeaf(environment, "retrieval.semantic-fallback-enabled", Boolean.class,
                properties.getRetrieval()::setSemanticFallbackEnabled, log);
        bindLegacyRagLeaf(environment, "retrieval.query-expansion.enabled", Boolean.class,
                properties.getRetrieval().getQueryExpansion()::setEnabled, log);
        bindLegacyRagLeaf(environment, "retrieval.query-expansion.max-keywords", Integer.class,
                properties.getRetrieval().getQueryExpansion()::setMaxKeywords, log);
        bindLegacyRagLeaf(environment, "object-scope.default-list-limit", Integer.class,
                properties.getObjectScope()::setDefaultListLimit, log);
        bindLegacyRagLeaf(environment, "object-scope.max-list-limit", Integer.class,
                properties.getObjectScope()::setMaxListLimit, log);
        bindLegacyRagLeaf(environment, "cleaner.enabled", Boolean.class, properties.getCleaner()::setEnabled, log);
        bindLegacyRagLeaf(environment, "cleaner.prompt", String.class, properties.getCleaner()::setPrompt, log);
        bindLegacyRagLeaf(environment, "cleaner.max-input-chars", Integer.class,
                properties.getCleaner()::setMaxInputChars, log);
        bindLegacyRagLeaf(environment, "cleaner.fail-open", Boolean.class, properties.getCleaner()::setFailOpen, log);
        bindLegacyRagLeaf(environment, "diagnostics.enabled", Boolean.class,
                properties.getDiagnostics()::setEnabled, log);
        bindLegacyRagLeaf(environment, "diagnostics.log-results", Boolean.class,
                properties.getDiagnostics()::setLogResults, log);
        bindLegacyRagLeaf(environment, "diagnostics.max-snippet-chars", Integer.class,
                properties.getDiagnostics()::setMaxSnippetChars, log);
        bindLegacyRagLeaf(environment, "keywords.scope", String.class, properties.getKeywords()::setScope, log);
        bindLegacyRagLeaf(environment, "keywords.max-input-chars", Integer.class,
                properties.getKeywords()::setMaxInputChars, log);
        bindLegacyRagLeaf(environment, "jobs.repository", String.class, properties.getJobs()::setRepository, log);
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

    private static <T> void bindLegacyRagLeaf(
            Environment environment,
            String propertyName,
            Class<T> type,
            Consumer<T> setter,
            Logger log) {
        String targetKey = RAG_PREFIX + "." + propertyName;
        if (trimToNull(environment.getProperty(targetKey)) != null) {
            return;
        }
        String legacyKey = LEGACY_RAG_PREFIX + "." + propertyName;
        Binder.get(environment).bind(legacyKey, Bindable.of(type)).ifBound(value -> {
            setter.accept(value);
            warn(log, legacyKey, targetKey);
        });
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

    public static final class RoutingDefaults {
        private final String defaultProvider;
        private final String defaultChatProvider;
        private final String defaultEmbeddingProvider;
        private final boolean legacyDefaultProviderConfigured;

        public RoutingDefaults(String defaultProvider,
                               String defaultChatProvider,
                               String defaultEmbeddingProvider,
                               boolean legacyDefaultProviderConfigured) {
            this.defaultProvider = defaultProvider;
            this.defaultChatProvider = defaultChatProvider;
            this.defaultEmbeddingProvider = defaultEmbeddingProvider;
            this.legacyDefaultProviderConfigured = legacyDefaultProviderConfigured;
        }

        public String defaultProvider() {
            return defaultProvider;
        }

        public String defaultChatProvider() {
            return defaultChatProvider;
        }

        public String defaultEmbeddingProvider() {
            return defaultEmbeddingProvider;
        }

        public boolean legacyDefaultProviderConfigured() {
            return legacyDefaultProviderConfigured;
        }
    }
}
