package studio.one.platform.ai.model.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EmbeddingSpaceIdTest {

    @Test
    void canonicalizesIdentifiersAndSortsSemanticOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("truncate", "END");
        options.put("encodingFormat", "float");

        EmbeddingSpaceContract contract = new EmbeddingSpaceContract(
                "V1", "Google-AI", " gemini-embedding-2 ", 768, "NONE",
                "RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY", "document-text", "1", options);

        assertThat(EmbeddingSpaceId.canonicalJson(contract)).isEqualTo(
                "{\"contractVersion\":\"v1\",\"providerFamily\":\"google-ai\","
                        + "\"apiModel\":\"gemini-embedding-2\",\"dimension\":768,"
                        + "\"normalizationPolicy\":\"none\","
                        + "\"indexTaskType\":\"retrieval_document\","
                        + "\"queryTaskType\":\"retrieval_query\","
                        + "\"inputTransformId\":\"document-text\","
                        + "\"inputTransformVersion\":\"1\","
                        + "\"semanticOptions\":{\"encodingFormat\":\"float\",\"truncate\":\"END\"}}");
        assertThat(EmbeddingSpaceId.from(contract)).isEqualTo(
                "es:v1:223ff0546aa99aabfc402affbb92ae3cb32e56979e5fe8a53a0cc7c4e48984b0");
    }

    @Test
    void changesWhenAResultAffectingFieldChanges() {
        EmbeddingSpaceContract first = contract(768);
        EmbeddingSpaceContract second = contract(1536);

        assertThat(EmbeddingSpaceId.from(first)).isNotEqualTo(EmbeddingSpaceId.from(second));
    }

    private EmbeddingSpaceContract contract(int dimension) {
        return new EmbeddingSpaceContract(
                "v1", "google-ai", "gemini-embedding-2", dimension, "none",
                "retrieval_document", "retrieval_query", null, null, Map.of());
    }
}
