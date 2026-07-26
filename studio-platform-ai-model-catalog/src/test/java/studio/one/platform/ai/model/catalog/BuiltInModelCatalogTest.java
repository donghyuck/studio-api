package studio.one.platform.ai.model.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelCapabilities;
import studio.one.platform.ai.model.ModelWorkload;

class BuiltInModelCatalogTest {

    @Test
    void loadsValidatedSnapshot() {
        DefaultModelCatalog catalog = BuiltInModelCatalog.load();

        assertThat(catalog.definitions()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(catalog.definitions()).allSatisfy(definition -> {
            assertThat(definition.catalogVersion()).isEqualTo("2026.07.23");
            assertThat(definition.sourceUrl()).startsWith("http");
            assertThat(definition.verifiedAt()).isNotBlank();
        });
    }

    @Test
    void resolvesCurrentApplicationAliases() {
        DefaultModelCatalog catalog = BuiltInModelCatalog.load();

        assertThat(catalog.find("gemma-3-4b")).get()
                .extracting(definition -> definition.apiModel())
                .isEqualTo("gemma-3-4b");
        assertThat(catalog.find("google/gemini-embedding-text@768")).get()
                .satisfies(definition -> {
                    assertThat(definition.supports(ModelWorkload.EMBEDDING)).isTrue();
                    assertThat(definition.dimensionPolicy().supported()).contains(768);
                });
    }

    @Test
    void keepsUnsupportedProvidersReferenceOnly() {
        DefaultModelCatalog catalog = BuiltInModelCatalog.load();

        assertThat(catalog.find("anthropic/claude-sonnet-4.6")).get()
                .extracting(definition -> definition.catalogTier())
                .isEqualTo(ModelCatalogTier.REFERENCE_ONLY);
    }

    @Test
    void exposesVerifiedProviderPromptCacheCapabilities() {
        DefaultModelCatalog catalog = BuiltInModelCatalog.load();

        assertThat(catalog.find("google/gemini-2.5-flash")).get()
                .satisfies(definition -> assertThat(definition.capabilities())
                        .contains(ModelCapabilities.PROMPT_CACHE_IMPLICIT));
        assertThat(catalog.find("openai/gpt-5.6-sol")).get()
                .satisfies(definition -> assertThat(definition.capabilities())
                        .contains(
                                ModelCapabilities.PROMPT_CACHE_IMPLICIT,
                                ModelCapabilities.PROMPT_CACHE_ROUTING_KEY,
                                ModelCapabilities.PROMPT_CACHE_EXPLICIT));
        assertThat(catalog.find("anthropic/claude-sonnet-4.6")).get()
                .satisfies(definition -> assertThat(definition.capabilities())
                        .contains(
                                ModelCapabilities.PROMPT_CACHE_IMPLICIT,
                                ModelCapabilities.PROMPT_CACHE_EXPLICIT));
    }
}
