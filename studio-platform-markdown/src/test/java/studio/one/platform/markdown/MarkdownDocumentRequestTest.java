package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.markdown.web.MarkdownDocumentRequest;
import studio.one.platform.markdown.web.MarkdownReextractRequest;

class MarkdownDocumentRequestTest {

    @Test
    void keepsLegacyEmbeddingModelIdSeparateFromProfileSelection() throws Exception {
        MarkdownDocumentRequest request = requestMapper().readValue("""
                {
                  "attachmentId": 7,
                  "runChunking": true,
                  "runRagIndex": true,
                  "runSkillExtraction": false,
                  "force": false,
                  "embeddingModelId": "google-ai/gemini-embedding-001@768"
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.embeddingModelId()).isEqualTo("google-ai/gemini-embedding-001@768");
        assertThat(request.embeddingProfileId()).isNull();
        assertThat(request.embeddingProvider()).isNull();
        assertThat(request.embeddingModel()).isNull();
    }

    @Test
    void acceptsEmbeddingDeploymentIdAsCanonicalSelection() throws Exception {
        MarkdownDocumentRequest request = requestMapper().readValue("""
                {
                  "attachmentId": 7,
                  "runChunking": true,
                  "runRagIndex": true,
                  "runSkillExtraction": false,
                  "force": false,
                  "embeddingDeploymentId": "document-multimodal-v1"
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.embeddingDeploymentId()).isEqualTo("document-multimodal-v1");
        assertThat(request.embeddingModelId()).isNull();
        assertThat(request.embeddingProfileId()).isNull();
    }

    @Test
    void defaultsOmittedOptionalBooleanOptionsToFalse() throws Exception {
        MarkdownDocumentRequest request = requestMapper().readValue("""
                {
                  "attachmentId": 13,
                  "runChunking": true,
                  "runRagIndex": true,
                  "runSkillExtraction": false,
                  "force": false
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.useLlmKeywordExtraction()).isFalse();
        assertThat(request.generateSkillEmbeddings()).isFalse();
    }

    @Test
    void defaultsNullOptionalBooleanOptionsToFalseForReextract() throws Exception {
        MarkdownReextractRequest request = requestMapper().readValue("""
                {
                  "runChunking": true,
                  "runRagIndex": true,
                  "runSkillExtraction": false,
                  "useLlmKeywordExtraction": null,
                  "generateSkillEmbeddings": null
                }
                """, MarkdownReextractRequest.class);

        assertThat(request.useLlmKeywordExtraction()).isFalse();
        assertThat(request.generateSkillEmbeddings()).isFalse();
    }

    @Test
    void preservesEnabledOptionalBooleanOptions() throws Exception {
        MarkdownDocumentRequest request = requestMapper().readValue("""
                {
                  "attachmentId": 13,
                  "runChunking": true,
                  "runRagIndex": true,
                  "runSkillExtraction": false,
                  "force": false,
                  "useLlmKeywordExtraction": true,
                  "generateSkillEmbeddings": true
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.useLlmKeywordExtraction()).isTrue();
        assertThat(request.generateSkillEmbeddings()).isTrue();
    }

    private ObjectMapper requestMapper() {
        return JsonMapper.builder().build();
    }
}
