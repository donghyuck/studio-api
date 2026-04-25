package studio.one.platform.ai.core.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.vector.VectorSearchRequest;

class RagRetrievalRequestContractTest {

    @Test
    void legacyRagSearchRequestConstructorKeepsEmptyFilter() {
        RagSearchRequest request = new RagSearchRequest("hello", 3);

        assertThat(request.query()).isEqualTo("hello");
        assertThat(request.topK()).isEqualTo(3);
        assertThat(request.metadataFilter().isEmpty()).isTrue();
    }

    @Test
    void ragSearchRequestCanCarryObjectScopeFilter() {
        RagSearchRequest request = new RagSearchRequest(
                "hello",
                3,
                MetadataFilter.objectScope(" attachment ", " 42 "));

        assertThat(request.metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(request.metadataFilter().objectId()).isEqualTo("42");
    }

    @Test
    void legacyVectorSearchRequestConstructorKeepsEmptyFilterAndMinScore() {
        VectorSearchRequest request = new VectorSearchRequest(List.of(0.1d, 0.2d), 5);

        assertThat(request.embedding()).containsExactly(0.1d, 0.2d);
        assertThat(request.topK()).isEqualTo(5);
        assertThat(request.metadataFilter().isEmpty()).isTrue();
        assertThat(request.minScore()).isNull();
    }

    @Test
    void vectorSearchRequestCanCarryFilterAndMinScore() {
        VectorSearchRequest request = new VectorSearchRequest(
                List.of(0.1d, 0.2d),
                5,
                MetadataFilter.objectScope("attachment", "42"),
                0.4d);

        assertThat(request.metadataFilter().hasObjectScope()).isTrue();
        assertThat(request.metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(request.metadataFilter().objectId()).isEqualTo("42");
        assertThat(request.minScore()).isEqualTo(0.4d);
        assertThat(request.hasMinScore()).isTrue();
    }
}
