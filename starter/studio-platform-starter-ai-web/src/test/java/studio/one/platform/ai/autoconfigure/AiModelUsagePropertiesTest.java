package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiModelUsagePropertiesTest {

    @Test
    void preservesDotsInBracketedModelPricingKeys() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "studio.ai.usage.pricing[gemini-2.5-pro].input-per-million-tokens", "1.25",
                "studio.ai.usage.pricing[gemini-2.5-pro].output-per-million-tokens", "10.00")));

        AiModelUsageProperties properties = Binder.get(environment)
                .bind("studio.ai.usage", AiModelUsageProperties.class)
                .orElseThrow(() -> new AssertionError("usage properties were not bound"));

        assertThat(properties.getPricing()).containsKey("gemini-2.5-pro");
        assertThat(properties.getPricing().get("gemini-2.5-pro").getInputPerMillionTokens())
                .isEqualByComparingTo("1.25");
    }
}
