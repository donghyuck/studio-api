package studio.one.platform.ai.core.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;

class RagEmbeddingSelectionContractTest {

    @Test
    void keepsRagRequestConstructorsBackwardCompatible() {
        RagIndexRequest indexRequest = new RagIndexRequest("doc-1", "text", Map.of());
        RagSearchRequest searchRequest = new RagSearchRequest("query", 3);
        RagIndexJobSourceRequest sourceRequest = new RagIndexJobSourceRequest(Map.of(), List.of(), false);

        assertThat(indexRequest.embeddingProfileId()).isNull();
        assertThat(indexRequest.embeddingProvider()).isNull();
        assertThat(indexRequest.embeddingModel()).isNull();
        assertThat(indexRequest.chunkingOptions()).isEqualTo(RagChunkingOptions.empty());
        assertThat(searchRequest.metadataFilter()).isEqualTo(MetadataFilter.empty());
        assertThat(searchRequest.embeddingProfileId()).isNull();
        assertThat(sourceRequest.embeddingProfileId()).isNull();
        assertThat(sourceRequest.chunkingOptions()).isEqualTo(RagChunkingOptions.empty());
    }

    @Test
    void carriesEmbeddingSelectionAcrossRagRequests() {
        RagIndexRequest indexRequest = new RagIndexRequest(
                "doc-1", "text", Map.of(), List.of(), false,
                "retrieval", "google", "gemini-embedding-001");
        RagSearchRequest searchRequest = new RagSearchRequest(
                "query", 3, MetadataFilter.empty(),
                "retrieval", "google", "gemini-embedding-001");
        RagIndexJobSourceRequest sourceRequest = new RagIndexJobSourceRequest(
                Map.of(), List.of(), false,
                "retrieval", "google", "gemini-embedding-001");

        assertThat(indexRequest.embeddingProfileId()).isEqualTo("retrieval");
        assertThat(searchRequest.embeddingProvider()).isEqualTo("google");
        assertThat(sourceRequest.embeddingModel()).isEqualTo("gemini-embedding-001");
    }

    @Test
    void normalizesAndValidatesRagChunkingOptions() {
        RagChunkingOptions options = new RagChunkingOptions("FIXED_SIZE", 300, 30, "TOKEN");

        assertThat(options.strategy()).isEqualTo("fixed-size");
        assertThat(options.unit()).isEqualTo("token");
        assertThatThrownBy(() -> new RagChunkingOptions("semantic", 300, 30, "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported chunking strategy");
        assertThatThrownBy(() -> new RagChunkingOptions("recursive", 300, 300, "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap must be less than maxSize");
    }

    @Test
    void profileDefaultsToTextDerivedInputSupport() {
        RagEmbeddingProfile profile = new RagEmbeddingProfile(
                "retrieval", "google", "gemini-embedding-001", 768, null, Map.of());

        assertThat(profile.supports(EmbeddingInputType.TEXT)).isTrue();
        assertThat(profile.supports(EmbeddingInputType.TABLE_TEXT)).isTrue();
        assertThat(profile.supports(EmbeddingInputType.IMAGE_CAPTION)).isTrue();
        assertThat(profile.supports(EmbeddingInputType.OCR_TEXT)).isTrue();
    }
}
