package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceContract;
import studio.one.platform.ai.model.catalog.BuiltInModelCatalog;
import studio.one.platform.ai.autoconfigure.migration.PostgresModelDataMigrationService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiAdapterProperties.class, ModelDeploymentProperties.class})
public class ModelDeploymentRegistryConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(PostgresModelDataMigrationService.class)
    public PostgresModelDataMigrationService postgresModelDataMigrationService(JdbcTemplate jdbcTemplate) {
        return new PostgresModelDataMigrationService(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ModelCatalog.class)
    public ModelCatalog builtInModelCatalog() {
        return BuiltInModelCatalog.load();
    }

    @Bean
    @Primary
    public ModelDeploymentRegistry modelDeploymentRegistry(
            ModelCatalog catalog,
            ModelDeploymentProperties deploymentProperties,
            AiAdapterProperties adapterProperties,
            Environment environment,
            @Qualifier("providerChatPorts") Map<String, ChatPort> providerChatPorts,
            @Qualifier("providerEmbeddingPorts") Map<String, EmbeddingPort> providerEmbeddingPorts,
            List<ProviderChatPortFactory> chatFactories,
            List<ProviderEmbeddingPortFactory> embeddingFactories,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        return buildRegistry(catalog, deploymentProperties, adapterProperties, environment,
                providerChatPorts, providerEmbeddingPorts, chatFactories, embeddingFactories,
                springAiChatModelProvider, springAiEmbeddingModelProvider);
    }

    ModelDeploymentRegistry modelDeploymentRegistry(
            ModelCatalog catalog,
            ModelDeploymentProperties deploymentProperties,
            AiAdapterProperties adapterProperties,
            Environment environment,
            Map<String, ChatPort> providerChatPorts,
            Map<String, EmbeddingPort> providerEmbeddingPorts) {
        return buildRegistry(catalog, deploymentProperties, adapterProperties, environment,
                providerChatPorts, providerEmbeddingPorts, null, null, null, null);
    }

    private ModelDeploymentRegistry buildRegistry(
            ModelCatalog catalog,
            ModelDeploymentProperties deploymentProperties,
            AiAdapterProperties adapterProperties,
            Environment environment,
            Map<String, ChatPort> providerChatPorts,
            Map<String, EmbeddingPort> providerEmbeddingPorts,
            List<ProviderChatPortFactory> chatFactories,
            List<ProviderEmbeddingPortFactory> embeddingFactories,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        Map<String, ModelDeploymentProperties.Deployment> configured = deploymentProperties.getModelDeployments();
        boolean deploymentAwarePorts = !configured.isEmpty() && chatFactories != null && embeddingFactories != null;
        Map<String, DeploymentCandidate> candidates = configured.isEmpty()
                ? synthesizeLegacy(catalog, adapterProperties)
                : configured(catalog, configured);
        Map<AiAdapterProperties.ProviderType, ProviderChatPortFactory> chatFactoryMap = deploymentAwarePorts
                ? factoryMap(chatFactories, ProviderChatPortFactory::supportedType) : Map.of();
        Map<AiAdapterProperties.ProviderType, ProviderEmbeddingPortFactory> embeddingFactoryMap = deploymentAwarePorts
                ? factoryMap(embeddingFactories, ProviderEmbeddingPortFactory::supportedType) : Map.of();

        Map<String, ModelDeployment> deployments = new LinkedHashMap<>();
        Map<String, ChatPort> chatPorts = new LinkedHashMap<>();
        Map<String, EmbeddingPort> embeddingPorts = new LinkedHashMap<>();
        for (Map.Entry<String, DeploymentCandidate> entry : candidates.entrySet()) {
            String deploymentId = normalize(entry.getKey());
            DeploymentCandidate candidate = entry.getValue();
            if (!candidate.enabled()) {
                continue;
            }
            AiAdapterProperties.Provider provider = adapterProperties.getProviders().get(candidate.providerRef());
            if (provider == null || !provider.isEnabled()) {
                throw new IllegalStateException("studio.ai.model-deployments." + deploymentId
                        + ".provider-ref has no enabled provider: " + candidate.providerRef());
            }
            validateChannel(deploymentId, candidate, provider, !deploymentAwarePorts);
            ModelDeployment deployment = new ModelDeployment(
                    deploymentId, candidate.providerRef(), candidate.definition(), candidate.workload(),
                    candidate.dimension(), true, candidate.embeddingContract());
            deployments.put(deploymentId, deployment);
            if (candidate.workload() == ModelWorkload.CHAT) {
                ChatPort port = deploymentAwarePorts
                        ? deploymentChatPort(deploymentId, candidate, provider, chatFactoryMap,
                                environment, springAiChatModelProvider)
                        : requiredPort(providerChatPorts, candidate.providerRef(), deploymentId);
                chatPorts.put(deploymentId, port);
            } else if (candidate.workload() == ModelWorkload.EMBEDDING) {
                EmbeddingPort port = deploymentAwarePorts
                        ? deploymentEmbeddingPort(deploymentId, candidate, provider, embeddingFactoryMap,
                                environment, springAiEmbeddingModelProvider)
                        : requiredPort(providerEmbeddingPorts, candidate.providerRef(), deploymentId);
                embeddingPorts.put(deploymentId, port);
            }
        }

        AiConfigurationMigration.RoutingDefaults legacyRouting =
                AiConfigurationMigration.resolveRouting(adapterProperties, environment, null);
        String defaultChat = firstNonBlank(
                deploymentProperties.getRouting().getDefaultChatDeployment(),
                deploymentForProvider(deployments, legacyRouting.defaultChatProvider(), ModelWorkload.CHAT));
        String defaultEmbedding = firstNonBlank(
                deploymentProperties.getRouting().getDefaultEmbeddingDeployment(),
                deploymentForProvider(deployments, legacyRouting.defaultEmbeddingProvider(), ModelWorkload.EMBEDDING));
        requireDefault(deployments, defaultChat, ModelWorkload.CHAT, "default-chat-deployment");
        requireDefault(deployments, defaultEmbedding, ModelWorkload.EMBEDDING, "default-embedding-deployment");
        return new DefaultModelDeploymentRegistry(
                deployments, chatPorts, embeddingPorts, defaultChat, defaultEmbedding);
    }

    private Map<String, DeploymentCandidate> configured(
            ModelCatalog catalog,
            Map<String, ModelDeploymentProperties.Deployment> configured) {
        Map<String, DeploymentCandidate> result = new LinkedHashMap<>();
        configured.forEach((id, value) -> {
            if (value.getProviderRef() == null || value.getModelRef() == null || value.getWorkload() == null) {
                throw new IllegalStateException("studio.ai.model-deployments." + id
                        + " requires provider-ref, model-ref and workload");
            }
            ModelDefinition definition = catalog.find(value.getModelRef())
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown model-ref for deployment " + id + ": " + value.getModelRef()));
            if (definition.catalogTier() == ModelCatalogTier.REFERENCE_ONLY) {
                throw new IllegalStateException("Deployment " + id + " uses REFERENCE_ONLY model "
                        + definition.catalogId());
            }
            Integer dimension = value.getDimension() == null
                    ? definition.dimensionPolicy().defaultDimension()
                    : value.getDimension();
            result.put(normalize(id), new DeploymentCandidate(
                    normalize(value.getProviderRef()), definition, value.getWorkload(), dimension, value.isEnabled(),
                    embeddingContract(definition, value.getWorkload(), dimension, value)));
        });
        return result;
    }

    private Map<String, DeploymentCandidate> synthesizeLegacy(
            ModelCatalog catalog,
            AiAdapterProperties properties) {
        Map<String, DeploymentCandidate> result = new LinkedHashMap<>();
        properties.getProviders().forEach((providerId, provider) -> {
            if (!provider.isEnabled()) {
                return;
            }
            addLegacy(result, catalog, providerId, provider.getChat(), ModelWorkload.CHAT);
            addLegacy(result, catalog, providerId, provider.getEmbedding(), ModelWorkload.EMBEDDING);
        });
        return result;
    }

    private void addLegacy(Map<String, DeploymentCandidate> result, ModelCatalog catalog, String providerId,
            AiAdapterProperties.Channel channel, ModelWorkload workload) {
        if (!channel.isEnabled() || channel.getModel() == null || channel.getModel().isBlank()) {
            return;
        }
        List<ModelDefinition> matches = catalog.definitions().stream()
                .filter(definition -> definition.supports(workload))
                .filter(definition -> definition.apiModel().equals(channel.getModel().trim()))
                .toList();
        if (matches.size() == 1) {
            String id = normalize(providerId) + "-" + workload.name().toLowerCase(Locale.ROOT);
            result.put(id, new DeploymentCandidate(normalize(providerId), matches.get(0), workload,
                    channel.getDimension() == null
                            ? matches.get(0).dimensionPolicy().defaultDimension()
                            : channel.getDimension(), true,
                    embeddingContract(matches.get(0), workload,
                            channel.getDimension() == null
                                    ? matches.get(0).dimensionPolicy().defaultDimension()
                                    : channel.getDimension(), null)));
        }
    }

    private void validateChannel(String deploymentId, DeploymentCandidate candidate,
            AiAdapterProperties.Provider provider, boolean validateLegacyModel) {
        AiAdapterProperties.Channel channel = candidate.workload() == ModelWorkload.CHAT
                ? provider.getChat() : provider.getEmbedding();
        if (!channel.isEnabled()) {
            throw new IllegalStateException("Deployment " + deploymentId + " references a disabled "
                    + candidate.workload() + " channel");
        }
        if (validateLegacyModel && channel.getModel() != null && !channel.getModel().isBlank()
                && !channel.getModel().trim().equals(candidate.definition().apiModel())) {
            throw new IllegalStateException("Deployment " + deploymentId + " model-ref resolves to "
                    + candidate.definition().apiModel() + " but provider channel uses " + channel.getModel());
        }
        if (validateLegacyModel && candidate.workload() == ModelWorkload.EMBEDDING && channel.getDimension() != null
                && candidate.dimension() != null && !channel.getDimension().equals(candidate.dimension())) {
            throw new IllegalStateException("Deployment " + deploymentId + " dimension " + candidate.dimension()
                    + " does not match provider channel dimension " + channel.getDimension());
        }
    }

    private ChatPort deploymentChatPort(
            String deploymentId,
            DeploymentCandidate candidate,
            AiAdapterProperties.Provider provider,
            Map<AiAdapterProperties.ProviderType, ProviderChatPortFactory> factories,
            Environment environment,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider) {
        ProviderChatPortFactory factory = factories.get(provider.getType());
        if (factory == null) {
            throw new IllegalStateException("Deployment " + deploymentId
                    + " has no chat port factory for provider type " + provider.getType());
        }
        return factory.createForDeployment(candidate.providerRef(), provider,
                candidate.definition().apiModel(), environment, springAiChatModelProvider);
    }

    private EmbeddingPort deploymentEmbeddingPort(
            String deploymentId,
            DeploymentCandidate candidate,
            AiAdapterProperties.Provider provider,
            Map<AiAdapterProperties.ProviderType, ProviderEmbeddingPortFactory> factories,
            Environment environment,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider) {
        ProviderEmbeddingPortFactory factory = factories.get(provider.getType());
        if (factory == null) {
            throw new IllegalStateException("Deployment " + deploymentId
                    + " has no embedding port factory for provider type " + provider.getType());
        }
        return factory.createForDeployment(candidate.providerRef(), provider,
                candidate.definition().apiModel(), candidate.dimension(), environment,
                springAiEmbeddingModelProvider);
    }

    private <T> Map<AiAdapterProperties.ProviderType, T> factoryMap(
            List<T> factories,
            Function<T, AiAdapterProperties.ProviderType> type) {
        return factories.stream().collect(Collectors.toMap(type, Function.identity()));
    }

    private <T> T requiredPort(Map<String, T> ports, String providerRef, String deploymentId) {
        T port = ports.get(providerRef);
        if (port == null) {
            throw new IllegalStateException("Deployment " + deploymentId
                    + " has no registered port for provider " + providerRef);
        }
        return port;
    }

    private String deploymentForProvider(
            Map<String, ModelDeployment> deployments, String provider, ModelWorkload workload) {
        if (provider == null) {
            return null;
        }
        return deployments.values().stream()
                .filter(value -> value.providerRef().equals(normalize(provider)) && value.workload() == workload)
                .map(ModelDeployment::deploymentId)
                .findFirst()
                .orElse(null);
    }

    private void requireDefault(Map<String, ModelDeployment> deployments, String id,
            ModelWorkload workload, String property) {
        if (deployments.isEmpty()) {
            return;
        }
        ModelDeployment deployment = deployments.get(normalize(id));
        if (deployment == null || deployment.workload() != workload) {
            throw new IllegalStateException("studio.ai.routing." + property
                    + " must reference an enabled " + workload + " deployment");
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : normalize(first);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private EmbeddingSpaceContract embeddingContract(
            ModelDefinition definition,
            ModelWorkload workload,
            Integer dimension,
            ModelDeploymentProperties.Deployment properties) {
        if (workload != ModelWorkload.EMBEDDING || dimension == null) {
            return null;
        }
        return new EmbeddingSpaceContract(
                "v1", definition.providerFamily(), definition.apiModel(), dimension,
                properties == null ? "provider-default" : properties.getNormalizationPolicy(),
                properties == null ? "provider-default" : properties.getIndexTaskType(),
                properties == null ? "provider-default" : properties.getQueryTaskType(),
                properties == null ? "text" : properties.getInputTransformId(),
                properties == null ? "1" : properties.getInputTransformVersion(),
                properties == null ? Map.of() : properties.getSemanticOptions());
    }

    private record DeploymentCandidate(
            String providerRef,
            ModelDefinition definition,
            ModelWorkload workload,
            Integer dimension,
            boolean enabled,
            EmbeddingSpaceContract embeddingContract) {
    }
}
