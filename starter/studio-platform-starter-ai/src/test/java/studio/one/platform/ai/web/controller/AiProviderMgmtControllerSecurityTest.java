package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;

class AiProviderMgmtControllerSecurityTest {

    @Test
    void legacyEmbeddingOptionsEndpointKeepsFeatureGateAndRequiresReadPermission() throws Exception {
        ConditionalOnProperty featureGate = AiProviderMgmtController.class
                .getAnnotation(ConditionalOnProperty.class);
        PreAuthorize authorization = AiProviderMgmtController.class
                .getDeclaredMethod("embeddingOptions")
                .getAnnotation(PreAuthorize.class);

        assertThat(featureGate).isNotNull();
        assertThat(featureGate.name()).containsExactly("enabled");
        assertThat(featureGate.havingValue()).isEqualTo("true");
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("services:ai_embedding").contains("read");
    }
}
