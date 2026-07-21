package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.markdown.web.MarkdownDocumentRequest;

class MarkdownDocumentRequestTest {

    @Test
    void acceptsCanonicalEmbeddingModelIdAsLegacyProfileSelection() throws Exception {
        MarkdownDocumentRequest request = new ObjectMapper().readValue("""
                {
                  "attachmentId": 7,
                  "runChunking": true,
                  "runRagIndex": true,
                  "embeddingModelId": "google-ai/gemini-embedding-001@768"
                }
                """, MarkdownDocumentRequest.class);

        assertThat(request.embeddingProfileId()).isEqualTo("google-ai/gemini-embedding-001@768");
        assertThat(request.embeddingProvider()).isNull();
        assertThat(request.embeddingModel()).isNull();
    }
}
