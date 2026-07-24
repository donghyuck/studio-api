package studio.one.platform.ai.model.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ModelCatalogValidatorTest {

    @Test
    void rejectsDuplicateAlias() {
        String json = """
                {
                  "catalogVersion": "test",
                  "models": [
                    {
                      "catalogId": "test/one", "providerFamily": "test", "apiModel": "one",
                      "workloads": ["CHAT"], "inputModalities": ["TEXT"], "lifecycle": "STABLE",
                      "aliases": ["shared"], "distribution": "LOCAL_CUSTOM", "catalogTier": "REFERENCE_ONLY",
                      "catalogSource": "test", "sourceUrl": "https://example.com/one", "verifiedAt": "2026-07-23"
                    },
                    {
                      "catalogId": "test/two", "providerFamily": "test", "apiModel": "two",
                      "workloads": ["CHAT"], "inputModalities": ["TEXT"], "lifecycle": "STABLE",
                      "aliases": ["shared"], "distribution": "LOCAL_CUSTOM", "catalogTier": "REFERENCE_ONLY",
                      "catalogSource": "test", "sourceUrl": "https://example.com/two", "verifiedAt": "2026-07-23"
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> new CatalogResourceLoader().load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(CatalogValidationException.class)
                .hasMessageContaining("duplicate catalog id or alias 'shared'");
    }
}
