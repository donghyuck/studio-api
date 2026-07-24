package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.model.Modality;
import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelLifecycle;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceId;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.ai.endpoints.base-path:/api/ai}")
public class ModelCatalogController {

    private final ModelCatalog catalog;
    private final ModelDeploymentRegistry registry;
    private final AiAdapterProperties adapterProperties;

    public ModelCatalogController(ModelCatalog catalog, ModelDeploymentRegistry registry) {
        this(catalog, registry, null);
    }

    public ModelCatalogController(
            ModelCatalog catalog,
            ModelDeploymentRegistry registry,
            AiAdapterProperties adapterProperties) {
        this.catalog = catalog;
        this.registry = registry;
        this.adapterProperties = adapterProperties;
    }

    @GetMapping("/models")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read') || @endpointAuthz.can('services:ai_embedding','read')")
    public ResponseEntity<ApiResponse<List<ModelInfo>>> models(
            @RequestParam(required = false) ModelWorkload workload,
            @RequestParam(defaultValue = "false") boolean effectiveOnly) {
        List<ModelInfo> values = catalog.definitions().stream()
                .filter(definition -> workload == null || definition.supports(workload))
                .map(this::modelInfo)
                .filter(value -> !effectiveOnly || "EFFECTIVE".equals(value.effectiveStatus()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(values));
    }

    @GetMapping("/deployments")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read') || @endpointAuthz.can('services:ai_embedding','read')")
    public ResponseEntity<ApiResponse<List<DeploymentInfo>>> deployments(
            @RequestParam(required = false) ModelWorkload workload) {
        return ResponseEntity.ok(ApiResponse.ok(registry.deployments(workload).stream()
                .map(this::deploymentInfo).toList()));
    }

    @GetMapping("/deployments/{id}")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read') || @endpointAuthz.can('services:ai_embedding','read')")
    public ResponseEntity<ApiResponse<DeploymentInfo>> deployment(@PathVariable String id) {
        ModelDeployment value = registry.find(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown model deployment: " + id));
        return ResponseEntity.ok(ApiResponse.ok(deploymentInfo(value)));
    }

    private ModelInfo modelInfo(ModelDefinition definition) {
        List<ModelDeployment> deployments = deployments(definition);
        boolean effective = !deployments.isEmpty();
        String status = effective ? "EFFECTIVE"
                : definition.catalogTier() == ModelCatalogTier.REFERENCE_ONLY
                        ? "ADAPTER_UNSUPPORTED" : "NOT_CONFIGURED";
        String adapterStatus = effective ? "READY"
                : definition.catalogTier() == ModelCatalogTier.REFERENCE_ONLY
                        ? "ADAPTER_UNSUPPORTED" : "NOT_CONFIGURED";
        String providerStatus = effective && deployments.stream().allMatch(this::providerConfigured)
                ? "UNVERIFIED" : "NOT_CONFIGURED";
        return new ModelInfo(
                definition.catalogId(), definition.displayName(), definition.description(),
                definition.providerFamily(), definition.apiModel(), definition.workloads(),
                definition.inputModalities(), effectiveModalities(deployments),
                definition.outputModalities(), definition.capabilities(),
                definition.dimensionPolicy().supported(), definition.dimensionPolicy().defaultDimension(),
                definition.lifecycle(), definition.catalogTier(), definition.distribution().name(),
                definition.license(), definition.sourceUrl(), definition.verifiedAt(),
                deployments.stream().map(ModelDeployment::deploymentId).toList(),
                deployments.stream().map(ModelDeployment::providerRef)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
                definition.catalogTier().name(), providerStatus,
                adapterStatus, status, statusReason(status));
    }

    private DeploymentInfo deploymentInfo(ModelDeployment deployment) {
        ModelDefinition definition = deployment.definition();
        boolean providerConfigured = providerConfigured(deployment);
        String providerStatus = providerConfigured ? "UNVERIFIED" : "NOT_CONFIGURED";
        String effectiveStatus = providerConfigured && deployment.enabled() ? "EFFECTIVE" : "UNAVAILABLE";
        return new DeploymentInfo(
                deployment.deploymentId(), definition.catalogId(), definition.displayName(),
                definition.description(), deployment.providerRef(), definition.providerFamily(), definition.apiModel(),
                deployment.workload(), definition.workloads(), definition.inputModalities(),
                effectiveModalities(deployment), definition.capabilities(),
                deployment.dimension(), embeddingSpaceId(deployment), definition.lifecycle(),
                definition.catalogTier().name(), providerStatus, "READY", effectiveStatus,
                providerConfigured
                        ? "Configured deployment; provider discovery has not been performed"
                        : "Deployment provider is not configured or is disabled");
    }

    private List<ModelDeployment> deployments(ModelDefinition definition) {
        return registry.deployments(null).stream()
                .filter(value -> value.definition().catalogId().equals(definition.catalogId()))
                .toList();
    }

    private Set<Modality> effectiveModalities(List<ModelDeployment> deployments) {
        return deployments.stream()
                .flatMap(deployment -> effectiveModalities(deployment).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<Modality> effectiveModalities(ModelDeployment deployment) {
        AiAdapterProperties.Provider provider = adapterProperties == null
                ? null : adapterProperties.getProviders().get(deployment.providerRef());
        if (adapterProperties != null && (provider == null || !provider.isEnabled())) {
            return Set.of();
        }
        // Current ChatRequest and EmbeddingPort adapters accept text payloads. Structured/OCR/image-caption
        // inputs are converted to text before the adapter boundary.
        return deployment.definition().inputModalities().contains(Modality.TEXT)
                ? Set.of(Modality.TEXT) : Set.of();
    }

    private boolean providerConfigured(ModelDeployment deployment) {
        if (adapterProperties == null) {
            return true;
        }
        AiAdapterProperties.Provider provider = adapterProperties.getProviders().get(deployment.providerRef());
        return provider != null && provider.isEnabled();
    }

    private String statusReason(String status) {
        return switch (status) {
            case "EFFECTIVE" -> "At least one configured deployment is effective; provider availability is unverified";
            case "ADAPTER_UNSUPPORTED" -> "Catalog entry is reference-only and has no supported adapter deployment";
            default -> "Catalog entry is not configured as a deployment";
        };
    }

    private String embeddingSpaceId(ModelDeployment deployment) {
        if (deployment.workload() != ModelWorkload.EMBEDDING || deployment.dimension() == null) {
            return null;
        }
        return EmbeddingSpaceId.from(deployment.embeddingContract());
    }

    public record ModelInfo(
            String catalogId, String displayName, String description, String providerFamily, String apiModel,
            Set<ModelWorkload> workloads, Set<Modality> declaredModalities, Set<Modality> effectiveModalities,
            Set<Modality> outputModalities,
            Set<String> capabilities, Set<Integer> dimensions, Integer defaultDimension,
            ModelLifecycle lifecycle, ModelCatalogTier catalogTier, String distribution, String license,
            String sourceUrl, String verifiedAt, List<String> deploymentIds, Set<String> providerRefs,
            String catalogStatus, String providerStatus, String adapterStatus,
            String effectiveStatus, String statusReason) {
    }

    public record DeploymentInfo(
            String deploymentId, String catalogId, String displayName, String description, String providerRef,
            String providerFamily, String apiModel, ModelWorkload workload, Set<ModelWorkload> workloads,
            Set<Modality> declaredModalities, Set<Modality> effectiveModalities,
            Set<String> capabilities, Integer dimension, String embeddingSpaceId,
            ModelLifecycle lifecycle, String catalogStatus, String providerStatus, String adapterStatus,
            String effectiveStatus, String statusReason) {
    }
}
