package studio.one.platform.chunking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ResolvedTokenizer;
import studio.one.platform.chunking.core.TokenizerPort;
import studio.one.platform.chunking.core.TokenizerResolver;

public class DefaultTokenizerResolver implements TokenizerResolver {

    private static final String EMBEDDING_PROVIDER = "embeddingProvider";
    private static final String EMBEDDING_MODEL = "embeddingModel";
    private static final String TOKENIZER_AUTO_DETECT = "tokenizerAutoDetect";

    private final ChunkingProperties.TokenizerProperties properties;
    private final Map<String, TokenizerPort> tokenizersByProvider;
    private final TokenizerPort approximateTokenizer;

    public DefaultTokenizerResolver(
            ChunkingProperties.TokenizerProperties properties,
            List<TokenizerPort> tokenizers) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.tokenizersByProvider = new LinkedHashMap<>();
        for (TokenizerPort tokenizer : tokenizers) {
            tokenizersByProvider.putIfAbsent(normalize(tokenizer.provider()), tokenizer);
        }
        this.approximateTokenizer = tokenizersByProvider.getOrDefault("approximate", new ApproximateTokenizer());
    }

    @Override
    public ResolvedTokenizer resolve(Map<String, Object> metadata) {
        Map<String, Object> values = metadata == null ? Map.of() : metadata;
        Optional<ResolvedTokenizer> explicit = explicit(values);
        if (explicit.isPresent()) {
            return explicit.get();
        }
        String model = text(values.get(EMBEDDING_MODEL));
        Optional<ResolvedTokenizer> configured = configuredMapping(model);
        if (configured.isPresent()) {
            return configured.get();
        }
        boolean autoDetect = autoDetectEnabled(values);
        Optional<ResolvedTokenizer> modelMapping = modelMapping(model, autoDetect);
        if (modelMapping.isPresent()) {
            return modelMapping.get();
        }
        Optional<ResolvedTokenizer> providerDefault = providerDefault(text(values.get(EMBEDDING_PROVIDER)), autoDetect);
        if (providerDefault.isPresent()) {
            return providerDefault.get();
        }
        if (properties.isFailOnUnknownModel()) {
            throw new IllegalArgumentException("Tokenizer mapping is not available for embedding model: " + model);
        }
        return fallback("No tokenizer mapping is available for embedding model: " + (model == null ? "unknown" : model));
    }

    private Optional<ResolvedTokenizer> explicit(Map<String, Object> metadata) {
        String provider = text(metadata.get(ChunkMetadata.KEY_TOKENIZER_PROVIDER));
        String encoding = text(metadata.get(ChunkMetadata.KEY_TOKENIZER_ENCODING));
        if (provider == null && encoding == null) {
            return Optional.empty();
        }
        return Optional.of(resolve(provider, encoding, text(metadata.get(ChunkMetadata.KEY_TOKENIZER_MODEL)),
                "explicit-config", "high"));
    }

    private Optional<ResolvedTokenizer> configuredMapping(String model) {
        if (model == null) {
            return Optional.empty();
        }
        ChunkingProperties.TokenizerMappingProperties mapping = properties.getMappings().get(model);
        if (mapping == null) {
            mapping = properties.getMappings().get(model.toLowerCase(Locale.ROOT));
        }
        if (mapping == null) {
            return Optional.empty();
        }
        return Optional.of(resolve(mapping.getProvider(), mapping.getEncoding(), mapping.getTokenizerModel(),
                "explicit-config", "high"));
    }

    private Optional<ResolvedTokenizer> modelMapping(String model, boolean autoDetect) {
        if (!autoDetect || model == null) {
            return Optional.empty();
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        if (normalized.equals("text-embedding-3-small")
                || normalized.equals("text-embedding-3-large")
                || normalized.equals("text-embedding-ada-002")) {
            return Optional.of(resolve("tiktoken", "cl100k_base", model, "model-mapping", "high"));
        }
        if (normalized.equals("gpt-4o") || normalized.equals("gpt-4o-mini")) {
            return Optional.of(resolve("tiktoken", "o200k_base", model, "model-mapping", "high"));
        }
        if (normalized.contains("kure") || normalized.contains("bge") || normalized.contains("kosimcse")) {
            return Optional.of(fallback("HuggingFace tokenizer adapter is not registered for model: " + model,
                    model, "auto-detected"));
        }
        return Optional.empty();
    }

    private Optional<ResolvedTokenizer> providerDefault(String provider, boolean autoDetect) {
        if (!autoDetect || provider == null) {
            return Optional.empty();
        }
        if (provider.equalsIgnoreCase("openai")) {
            return Optional.of(resolve("tiktoken", "cl100k_base", null, "provider-default", "medium"));
        }
        if (provider.equalsIgnoreCase("huggingface") || provider.equalsIgnoreCase("local")) {
            return Optional.of(fallback("HuggingFace tokenizer adapter is not registered for provider: " + provider,
                    null, "provider-default"));
        }
        return Optional.empty();
    }

    private boolean autoDetectEnabled(Map<String, Object> metadata) {
        Object value = metadata.get(TOKENIZER_AUTO_DETECT);
        if (value instanceof Boolean bool) {
            return properties.isAutoDetect() && bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return properties.isAutoDetect() && Boolean.parseBoolean(text.trim());
        }
        return properties.isAutoDetect();
    }

    private ResolvedTokenizer resolve(
            String provider,
            String encoding,
            String tokenizerModel,
            String selectionSource,
            String confidence) {
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null && encoding != null) {
            normalizedProvider = "tiktoken";
        }
        if ("tiktoken".equals(normalizedProvider) && encoding != null) {
            return new ResolvedTokenizer(new TiktokenTokenizerAdapter(encoding), "tiktoken", encoding, tokenizerModel,
                    selectionSource, confidence, false, List.of());
        }
        TokenizerPort tokenizer = tokenizersByProvider.get(normalizedProvider);
        if (tokenizer != null) {
            return new ResolvedTokenizer(tokenizer, tokenizer.provider(), tokenizer.encodingName(), tokenizerModel,
                    selectionSource, confidence, false, List.of());
        }
        return fallback("Tokenizer provider is not registered: " + provider, tokenizerModel, selectionSource);
    }

    private ResolvedTokenizer fallback(String warning) {
        return fallback(warning, null, "fallback");
    }

    private ResolvedTokenizer fallback(String warning, String tokenizerModel, String selectionSource) {
        return new ResolvedTokenizer(approximateTokenizer, approximateTokenizer.provider(),
                approximateTokenizer.encodingName(), tokenizerModel, selectionSource, "low", true, List.of(warning));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
