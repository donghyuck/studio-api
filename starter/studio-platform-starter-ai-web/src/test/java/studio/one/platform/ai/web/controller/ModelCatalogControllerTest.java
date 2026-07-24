package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import studio.one.platform.ai.model.ModelCatalog;
import studio.one.platform.ai.model.Modality;
import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelDimensionPolicy;
import studio.one.platform.ai.model.ModelDistribution;
import studio.one.platform.ai.model.ModelLifecycle;
import studio.one.platform.ai.model.ModelWorkload;

class ModelCatalogControllerTest {

    @Test
    void discoveryEndpointsRequireAiReadPermission() throws Exception {
        for (String method : List.of("models", "deployments", "deployment")) {
            var candidate = java.util.Arrays.stream(ModelCatalogController.class.getDeclaredMethods())
                    .filter(value -> value.getName().equals(method))
                    .filter(value -> value.getAnnotation(PreAuthorize.class) != null)
                    .findFirst().orElseThrow();
            PreAuthorize authorization = candidate.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as(method).isNotNull();
            assertThat(authorization.value()).contains("services:ai_chat").contains("services:ai_embedding");
        }
        assertThat(java.util.Arrays.stream(ModelCatalogController.DeploymentInfo.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("baseUrl", "apiKey", "credential", "authorization");
    }

    @Test
    void deploymentResponseExposesCanonicalIdentityWithoutConnectionSettings() {
        ModelCatalog catalog = mock(ModelCatalog.class);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        var definition = definition();
        var deployment = new ModelDeployment(
                "humanities-text-v1", "google-embedding", definition, ModelWorkload.EMBEDDING, 768, true);
        when(registry.deployments(null)).thenReturn(List.of(deployment));
        when(registry.deployments(ModelWorkload.EMBEDDING)).thenReturn(List.of(deployment));
        when(registry.find("humanities-text-v1")).thenReturn(Optional.of(deployment));
        ModelCatalogController controller = new ModelCatalogController(catalog, registry);

        var response = controller.deployment("humanities-text-v1").getBody().getData();

        assertThat(response.deploymentId()).isEqualTo("humanities-text-v1");
        assertThat(response.catalogId()).isEqualTo("google/gemini-embedding-001");
        assertThat(response.embeddingSpaceId()).startsWith("es:v1:");
        assertThat(response.providerStatus()).isEqualTo("UNVERIFIED");
        assertThat(response.adapterStatus()).isEqualTo("READY");
        assertThat(response.effectiveStatus()).isEqualTo("EFFECTIVE");
        assertThat(response.declaredModalities()).containsExactlyInAnyOrder(Modality.TEXT, Modality.IMAGE);
        assertThat(response.effectiveModalities()).containsExactly(Modality.TEXT);
    }

    @Test
    void effectiveModelFilterReturnsConfiguredModelsOnly() {
        ModelCatalog catalog = mock(ModelCatalog.class);
        ModelDeploymentRegistry registry = mock(ModelDeploymentRegistry.class);
        var definition = definition();
        var deployment = new ModelDeployment(
                "humanities-text-v1", "google-embedding", definition, ModelWorkload.EMBEDDING, 768, true);
        when(registry.deployments(null)).thenReturn(List.of(deployment));
        when(catalog.definitions()).thenReturn(List.of(definition));
        ModelCatalogController controller = new ModelCatalogController(catalog, registry);

        var models = controller.models(ModelWorkload.EMBEDDING, true).getBody().getData();

        assertThat(models).extracting(ModelCatalogController.ModelInfo::catalogId)
                .containsExactly("google/gemini-embedding-001");
        assertThat(models.get(0).deploymentIds()).containsExactly("humanities-text-v1");
        assertThat(models.get(0).providerRefs()).containsExactly("google-embedding");
    }

    private ModelDefinition definition() {
        ModelDefinition definition = mock(ModelDefinition.class);
        when(definition.catalogId()).thenReturn("google/gemini-embedding-001");
        when(definition.displayName()).thenReturn("Gemini Embedding 001");
        when(definition.providerFamily()).thenReturn("google");
        when(definition.apiModel()).thenReturn("gemini-embedding-001");
        when(definition.supports(ModelWorkload.EMBEDDING)).thenReturn(true);
        when(definition.workloads()).thenReturn(Set.of(ModelWorkload.EMBEDDING));
        when(definition.inputModalities()).thenReturn(Set.of(Modality.TEXT, Modality.IMAGE));
        when(definition.outputModalities()).thenReturn(Set.of());
        when(definition.capabilities()).thenReturn(Set.of());
        when(definition.dimensionPolicy()).thenReturn(new ModelDimensionPolicy(Set.of(768), 768));
        when(definition.lifecycle()).thenReturn(ModelLifecycle.STABLE);
        when(definition.catalogTier()).thenReturn(ModelCatalogTier.ADAPTER_READY);
        when(definition.distribution()).thenReturn(ModelDistribution.MANAGED_API);
        return definition;
    }
}
