package studio.one.platform.ai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.core.rag.external.ExternalEvidenceRequest;

class OfficialEvidenceGatewayProviderTest {

    @Test
    void acceptsOnlyConfiguredHttpsGateway() {
        AiWebRagProperties.ExternalSourcesProperties properties = validProperties();

        OfficialEvidenceGatewayProvider provider = new OfficialEvidenceGatewayProvider(
                properties,
                JsonMapper.builder().build());

        assertThat(provider.providerId()).isEqualTo("official-evidence-gateway");
        assertThat(provider.supports(new ExternalEvidenceRequest(
                "질문",
                "KR",
                java.time.LocalDate.of(2026, 7, 28),
                "ko",
                3))).isTrue();
    }

    @Test
    void rejectsHttpOrUnallowlistedGatewayAtStartup() {
        AiWebRagProperties.ExternalSourcesProperties properties = validProperties();
        properties.setGatewayUrl("http://evidence.internal.example/api/search");

        assertThatThrownBy(() -> new OfficialEvidenceGatewayProvider(
                properties,
                JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted https host");

        properties.setGatewayUrl("https://other.example/api/search");
        assertThatThrownBy(() -> new OfficialEvidenceGatewayProvider(
                properties,
                JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted https host");
    }

    @Test
    void requiresCredentialAndOfficialSourceAllowlist() {
        AiWebRagProperties.ExternalSourcesProperties missingCredential = validProperties();
        missingCredential.setApiKey(" ");

        assertThatThrownBy(() -> new OfficialEvidenceGatewayProvider(
                missingCredential,
                JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api key");

        AiWebRagProperties.ExternalSourcesProperties properties = validProperties();
        properties.setSourceAllowedHosts(Set.of());
        AiWebRagProperties.ExternalSourcesProperties missingAllowlist = properties;
        assertThatThrownBy(() -> new OfficialEvidenceGatewayProvider(
                missingAllowlist,
                JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed hosts");
    }

    @Test
    void parsesOnlyAllowlistedCanonicalOfficialSources() throws Exception {
        OfficialEvidenceGatewayProvider provider = new OfficialEvidenceGatewayProvider(
                validProperties(),
                JsonMapper.builder().build());
        String response = """
                {
                  "results": [
                    {
                      "evidenceId": "law-1",
                      "sourceType": "STATUTE",
                      "title": "근로기준법",
                      "publisher": "국가법령정보센터",
                      "canonicalUrl": "https://law.go.kr/example",
                      "publishedDate": "2026-01-01",
                      "effectiveDate": "2026-02-01",
                      "exactText": "공식 원문의 연속 발췌문",
                      "score": 0.95
                    },
                    {
                      "evidenceId": "unsafe-1",
                      "sourceType": "STATUTE",
                      "title": "외부 문서",
                      "publisher": "외부",
                      "canonicalUrl": "https://untrusted.example/rule",
                      "exactText": "허용해서는 안 되는 발췌문",
                      "score": 1.0
                    }
                  ]
                }
                """;

        var evidence = provider.parseGatewayResponse(response.getBytes(StandardCharsets.UTF_8));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).canonicalUri()).isEqualTo(java.net.URI.create("https://law.go.kr/example"));
        assertThat(evidence.get(0).exactText()).isEqualTo("공식 원문의 연속 발췌문");
        assertThat(evidence.get(0).contentHash()).hasSize(64);
        assertThat(evidence.get(0).metadata()).containsEntry("providerId", "official-evidence-gateway");
    }

    private AiWebRagProperties.ExternalSourcesProperties validProperties() {
        AiWebRagProperties.ExternalSourcesProperties properties =
                new AiWebRagProperties.ExternalSourcesProperties();
        properties.setEnabled(true);
        properties.setGatewayUrl("https://evidence.internal.example/api/search");
        properties.setApiKey("test-secret");
        properties.setGatewayAllowedHosts(Set.of("evidence.internal.example"));
        properties.setSourceAllowedHosts(Set.of("open.law.go.kr", "law.go.kr"));
        return properties;
    }
}
