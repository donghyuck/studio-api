package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceProvider;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceRequest;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceSourceType;

class RagExternalEvidenceServiceTest {

    @Test
    void doesNotCallProviderForDocumentOnlyScope() {
        CountingProvider provider = new CountingProvider();
        RagExternalEvidenceService service = new RagExternalEvidenceService(List.of(provider));

        RagExternalEvidenceService.Result result =
                service.retrieve("question", policy(false), null);

        assertThat(result.status()).isEqualTo(RagExternalEvidenceService.Status.NOT_REQUESTED);
        assertThat(provider.calls).isZero();
    }

    @Test
    void acceptsOnlyVerifiedHttpsEvidenceAndDeduplicatesIt() {
        ExternalEvidence valid = evidence("https://law.example.test/rule", "hash-1");
        ExternalEvidenceProvider provider = new ExternalEvidenceProvider() {
            @Override
            public String providerId() {
                return "official-test";
            }

            @Override
            public boolean supports(ExternalEvidenceRequest request) {
                return true;
            }

            @Override
            public List<ExternalEvidence> retrieve(ExternalEvidenceRequest request) {
                return List.of(valid, valid, evidence("http://unsafe.test/rule", "hash-2"));
            }
        };

        RagExternalEvidenceService.Result result =
                new RagExternalEvidenceService(List.of(provider))
                        .retrieve("question", policy(true), null);

        assertThat(result.status()).isEqualTo(RagExternalEvidenceService.Status.COMPLETE);
        assertThat(result.evidence()).containsExactly(valid);
    }

    @Test
    void reportsProviderFailureWithoutExposingProviderException() {
        ExternalEvidenceProvider provider = new ExternalEvidenceProvider() {
            @Override
            public String providerId() {
                return "failing-test";
            }

            @Override
            public boolean supports(ExternalEvidenceRequest request) {
                return true;
            }

            @Override
            public List<ExternalEvidence> retrieve(ExternalEvidenceRequest request) {
                throw new IllegalStateException("secret provider detail");
            }
        };

        RagExternalEvidenceService.Result result =
                new RagExternalEvidenceService(List.of(provider))
                        .retrieve("question", policy(true), null);

        assertThat(result.status()).isEqualTo(RagExternalEvidenceService.Status.FAILED);
        assertThat(result.reasonCode())
                .isEqualTo(RagExternalEvidenceService.ReasonCode.PROVIDER_FAILURE);
    }

    private ResolvedRagSourcePolicy policy(boolean external) {
        AiWebRagProperties.SourcePolicyProperties properties =
                new AiWebRagProperties.SourcePolicyProperties();
        properties.setClientSelectionEnabled(true);
        return new RagSourcePolicyResolver(properties, true)
                .resolve(external ? "DOCUMENT_AND_OFFICIAL_EXTERNAL" : "DOCUMENT_ONLY");
    }

    private ExternalEvidence evidence(String uri, String hash) {
        return new ExternalEvidence(
                "evidence-1",
                ExternalEvidenceSourceType.STATUTE,
                "Official rule",
                "Official publisher",
                URI.create(uri),
                null,
                null,
                Instant.parse("2026-07-28T00:00:00Z"),
                "Verified source text",
                hash,
                1.0d,
                Map.of());
    }

    private static final class CountingProvider implements ExternalEvidenceProvider {
        private int calls;

        @Override
        public String providerId() {
            return "counting";
        }

        @Override
        public boolean supports(ExternalEvidenceRequest request) {
            return true;
        }

        @Override
        public List<ExternalEvidence> retrieve(ExternalEvidenceRequest request) {
            calls++;
            return List.of();
        }
    }
}
