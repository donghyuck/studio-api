package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;

class RagSourcePolicyResolverTest {

    @Test
    void defaultsToDocumentOnly() {
        ResolvedRagSourcePolicy resolved = RagSourcePolicyResolver.defaults().resolve(null);

        assertThat(resolved.effectiveScope()).isEqualTo(RagSourceScope.DOCUMENT_ONLY);
        assertThat(resolved.clamped()).isFalse();
    }

    @Test
    void allowsOfficialExternalScopeWhenConfiguredAndProviderExists() {
        AiWebRagProperties.SourcePolicyProperties properties = properties(
                RagSourceScope.DOCUMENT_ONLY,
                RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL,
                true);
        RagSourcePolicyResolver resolver = new RagSourcePolicyResolver(properties, true);

        ResolvedRagSourcePolicy resolved =
                resolver.resolve("DOCUMENT_AND_OFFICIAL_EXTERNAL");

        assertThat(resolved.effectiveScope())
                .isEqualTo(RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL);
        assertThat(resolved.clamped()).isFalse();
        assertThat(resolver.availableScopes()).containsExactly(
                "DOCUMENT_ONLY",
                "DOCUMENT_AND_OFFICIAL_EXTERNAL");
    }

    @Test
    void clampsExternalScopeWhenProviderIsUnavailable() {
        AiWebRagProperties.SourcePolicyProperties properties = properties(
                RagSourceScope.DOCUMENT_ONLY,
                RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL,
                true);
        RagSourcePolicyResolver resolver = new RagSourcePolicyResolver(properties, false);

        ResolvedRagSourcePolicy resolved =
                resolver.resolve("DOCUMENT_AND_OFFICIAL_EXTERNAL");

        assertThat(resolved.effectiveScope()).isEqualTo(RagSourceScope.DOCUMENT_ONLY);
        assertThat(resolved.clamped()).isTrue();
        assertThat(resolved.reasonCode())
                .isEqualTo(ResolvedRagSourcePolicy.ReasonCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void rejectsInvalidConfigurationAndUnknownScope() {
        AiWebRagProperties.SourcePolicyProperties invalid = properties(
                RagSourceScope.DOCUMENT_AND_OFFICIAL_EXTERNAL,
                RagSourceScope.DOCUMENT_ONLY,
                true);

        assertThatThrownBy(() -> new RagSourcePolicyResolver(invalid, true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RagSourceScope.parse("internet"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private AiWebRagProperties.SourcePolicyProperties properties(
            RagSourceScope defaultScope,
            RagSourceScope maximumScope,
            boolean clientSelectionEnabled) {
        AiWebRagProperties.SourcePolicyProperties properties =
                new AiWebRagProperties.SourcePolicyProperties();
        properties.setDefaultScope(defaultScope);
        properties.setMaximumScope(maximumScope);
        properties.setClientSelectionEnabled(clientSelectionEnabled);
        return properties;
    }
}
