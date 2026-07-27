package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.markdown.web.MarkdownDocumentRequest;

class MarkdownDocumentRequestTest {

    @Test
    void keepsLegacyEmbeddingModelIdSeparateFromProfileSelection() throws Exception {
        MarkdownDocumentRequest request = new ObjectMapper().readValue("""
                {
                  "attachmentId": 7,
                  "runChunking": true,
                  "runRagIndex": true,
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
        MarkdownDocumentRequest request = new ObjectMapper().readValue("""
                {
                  "attachmentId": 7,
                  "runChunking": true,
                  "runRagIndex": true,
                  "embeddingDeploymentId": "document-multimodal-v1"
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.embeddingDeploymentId()).isEqualTo("document-multimodal-v1");
        assertThat(request.embeddingModelId()).isNull();
        assertThat(request.embeddingProfileId()).isNull();
    }
}
