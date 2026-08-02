package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;

class RagAnswerPolicyResolverTest {

    @Test
    void resolvesDefaultAllowedRequestAndServerClamp() {
        RagAnswerPolicyResolver defaults = resolver(
                RagAnswerMode.GROUNDED_INFERENCE,
                RagAnswerMode.GROUNDED_INFERENCE,
                true);

        assertThat(defaults.resolve(null).effectiveMode()).isEqualTo(RagAnswerMode.GROUNDED_INFERENCE);
        assertThat(defaults.resolve("STRICT_GROUNDED").effectiveMode()).isEqualTo(RagAnswerMode.STRICT_GROUNDED);

        RagAnswerPolicyResolver strictServer = resolver(
                RagAnswerMode.STRICT_GROUNDED,
                RagAnswerMode.STRICT_GROUNDED,
                true);
        ResolvedRagAnswerPolicy clamped = strictServer.resolve("GROUNDED_INFERENCE");

        assertThat(clamped.effectiveMode()).isEqualTo(RagAnswerMode.STRICT_GROUNDED);
        assertThat(clamped.clamped()).isTrue();
        assertThat(clamped.reasonCode()).isEqualTo(ResolvedRagAnswerPolicy.ReasonCode.SERVER_MAXIMUM);
    }

    @Test
    void ignoresSelectionWhenClientSelectionIsDisabled() {
        RagAnswerPolicyResolver resolver = resolver(
                RagAnswerMode.GROUNDED_INFERENCE,
                RagAnswerMode.GROUNDED_INFERENCE,
                false);

        ResolvedRagAnswerPolicy resolved = resolver.resolve("STRICT_GROUNDED");

        assertThat(resolved.effectiveMode()).isEqualTo(RagAnswerMode.GROUNDED_INFERENCE);
        assertThat(resolved.reasonCode())
                .isEqualTo(ResolvedRagAnswerPolicy.ReasonCode.CLIENT_SELECTION_DISABLED);
    }

    @Test
    void rejectsInvalidConfigurationAndUnknownMode() {
        assertThatThrownBy(() -> resolver(
                RagAnswerMode.GROUNDED_INFERENCE,
                RagAnswerMode.STRICT_GROUNDED,
                true))
                .isInstanceOf(IllegalStateException.class);

        RagAnswerPolicyResolver resolver = RagAnswerPolicyResolver.defaults();
        assertThatThrownBy(() -> resolver.resolve("UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(400);
    }

    @Test
    void partialListFeatureFlagChangesPolicyFingerprint() {
        AiWebRagProperties.AnswerPolicyProperties disabled =
                new AiWebRagProperties.AnswerPolicyProperties();
        AiWebRagProperties.AnswerPolicyProperties enabled =
                new AiWebRagProperties.AnswerPolicyProperties();
        enabled.setFactualListPartialAnswerEnabled(true);

        String disabledFingerprint = new RagAnswerPolicyResolver(disabled).resolve(null).fingerprint();
        String enabledFingerprint = new RagAnswerPolicyResolver(enabled).resolve(null).fingerprint();

        assertThat(enabledFingerprint).isNotEqualTo(disabledFingerprint);
    }

    private RagAnswerPolicyResolver resolver(
            RagAnswerMode defaultMode,
            RagAnswerMode maximumMode,
            boolean clientSelectionEnabled) {
        AiWebRagProperties.AnswerPolicyProperties properties =
                new AiWebRagProperties.AnswerPolicyProperties();
        properties.setDefaultMode(defaultMode);
        properties.setMaximumMode(maximumMode);
        properties.setClientSelectionEnabled(clientSelectionEnabled);
        return new RagAnswerPolicyResolver(properties);
    }
}
