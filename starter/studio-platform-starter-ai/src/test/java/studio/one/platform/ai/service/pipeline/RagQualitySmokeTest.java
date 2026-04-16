package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;

class RagQualitySmokeTest {

    private static final String ATTACHMENT = "attachment";
    private static final String POLICY_ATTACHMENT_ID = "policy-attachment";
    private static final String SUMMARY_ATTACHMENT_ID = "summary-attachment";

    private InMemoryVectorStore vectorStore;
    private RagPipelineService ragPipelineService;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
        ragPipelineService = DefaultRagPipelineService.create(
                new KeywordEmbeddingPort(),
                vectorStore,
                new ParagraphTextChunker(),
                Caffeine.newBuilder().build(),
                Retry.of("rag-quality-smoke", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build()),
                null);
    }

    @Test
    void shouldMapKoreanUploadLimitQueryToExpectedPolicyChunk() {
        indexFixture("korean-policy", "rag-fixtures/korean-policy.md", POLICY_ATTACHMENT_ID);

        List<RagSearchResult> results = ragPipelineService.searchByObject(
                new RagSearchRequest("업로드 파일 크기 제한은 몇 MB인가요?", 3),
                ATTACHMENT,
                POLICY_ATTACHMENT_ID);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).content()).contains("50MB");
        assertThat(results.get(0).metadata())
                .containsEntry("objectType", ATTACHMENT)
                .containsEntry("objectId", POLICY_ATTACHMENT_ID);
    }

    @Test
    void shouldNotReturnChunksFromAnotherAttachmentInObjectScopedSearch() {
        indexFixture("korean-policy", "rag-fixtures/korean-policy.md", POLICY_ATTACHMENT_ID);
        indexFixture("attachment-summary", "rag-fixtures/attachment-summary.md", SUMMARY_ATTACHMENT_ID);

        List<RagSearchResult> results = ragPipelineService.searchByObject(
                new RagSearchRequest("파일 업로드 제한과 첨부 파일 보존 기간", 5),
                ATTACHMENT,
                POLICY_ATTACHMENT_ID);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> assertThat(result.metadata())
                .containsEntry("objectType", ATTACHMENT)
                .containsEntry("objectId", POLICY_ATTACHMENT_ID));
        assertThat(results)
                .extracting(RagSearchResult::content)
                .noneMatch(content -> content.contains("첨부 파일 보존 기간") || content.contains("고객 지원 지표"));
    }

    @Test
    void shouldPreserveChunkOrderWhenListingObjectChunks() {
        indexFixture("korean-policy", "rag-fixtures/korean-policy.md", POLICY_ATTACHMENT_ID);

        List<RagSearchResult> results = ragPipelineService.listByObject(ATTACHMENT, POLICY_ATTACHMENT_ID, 10);

        assertThat(results).hasSizeGreaterThanOrEqualTo(4);
        assertThat(results)
                .extracting(result -> result.metadata().get("chunkOrder"))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(results.get(0).content()).contains("첨부 파일 업로드 정책");
        assertThat(results.get(2).content()).contains("50MB");
    }

    private void indexFixture(String documentId, String resourcePath, String objectId) {
        ragPipelineService.index(new RagIndexRequest(
                documentId,
                loadResource(resourcePath),
                Map.of(
                        "objectType", ATTACHMENT,
                        "objectId", objectId,
                        "source", resourcePath)));
    }

    private String loadResource(String resourcePath) {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(stream).as("fixture resource %s", resourcePath).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read fixture " + resourcePath, ex);
        }
    }

    private static final class ParagraphTextChunker implements TextChunker {

        @Override
        public List<TextChunk> chunk(String documentId, String text) {
            String[] paragraphs = text.replace("\r\n", "\n").replace("\r", "\n").trim().split("\\n\\s*\\n");
            List<TextChunk> chunks = new ArrayList<>(paragraphs.length);
            for (int i = 0; i < paragraphs.length; i++) {
                String paragraph = paragraphs[i].trim();
                if (!paragraph.isBlank()) {
                    chunks.add(new TextChunk(documentId + "-" + i, paragraph));
                }
            }
            return chunks;
        }
    }

    private static final class KeywordEmbeddingPort implements EmbeddingPort {

        @Override
        public EmbeddingResponse embed(EmbeddingRequest request) {
            List<EmbeddingVector> vectors = request.texts().stream()
                    .map(text -> new EmbeddingVector("fixture", vectorize(text)))
                    .toList();
            return new EmbeddingResponse(vectors);
        }
    }

    private static final class InMemoryVectorStore implements VectorStorePort {

        private final Map<String, VectorDocument> documents = new LinkedHashMap<>();

        @Override
        public void upsert(List<VectorDocument> documents) {
            documents.forEach(document -> this.documents.put(document.id(), document));
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return rank(documents.values(), request.embedding(), request.topK());
        }

        @Override
        public boolean exists(String objectType, String objectId) {
            return documents.values().stream().anyMatch(document -> matchesObject(document, objectType, objectId));
        }

        @Override
        public List<VectorSearchResult> searchByObject(String objectType, String objectId, VectorSearchRequest request) {
            return rank(scopedDocuments(objectType, objectId), request.embedding(), request.topK());
        }

        @Override
        public List<VectorSearchResult> hybridSearch(String query,
                VectorSearchRequest request,
                double vectorWeight,
                double lexicalWeight) {
            return hybridRank(documents.values(), query, request, vectorWeight, lexicalWeight);
        }

        @Override
        public List<VectorSearchResult> hybridSearchByObject(String query,
                String objectType,
                String objectId,
                VectorSearchRequest request,
                double vectorWeight,
                double lexicalWeight) {
            return hybridRank(scopedDocuments(objectType, objectId), query, request, vectorWeight, lexicalWeight);
        }

        @Override
        public List<VectorSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return scopedDocuments(objectType, objectId).stream()
                    .sorted(Comparator.comparingInt(InMemoryVectorStore::chunkOrder))
                    .limit(limit)
                    .map(document -> new VectorSearchResult(document, 1.0))
                    .toList();
        }

        private List<VectorDocument> scopedDocuments(String objectType, String objectId) {
            return documents.values().stream()
                    .filter(document -> matchesObject(document, objectType, objectId))
                    .toList();
        }

        private List<VectorSearchResult> hybridRank(Iterable<VectorDocument> candidates,
                String query,
                VectorSearchRequest request,
                double vectorWeight,
                double lexicalWeight) {
            Set<String> queryTerms = terms(query);
            return stream(candidates)
                    .map(document -> {
                        double vectorScore = cosine(request.embedding(), document.embedding());
                        double lexicalScore = lexicalScore(queryTerms, document.content());
                        double score = (vectorScore * vectorWeight) + (lexicalScore * lexicalWeight);
                        return new VectorSearchResult(document, score);
                    })
                    .filter(result -> result.score() > 0)
                    .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                            .thenComparing(result -> chunkOrder(result.document())))
                    .limit(request.topK())
                    .toList();
        }

        private List<VectorSearchResult> rank(Iterable<VectorDocument> candidates, List<Double> queryEmbedding, int topK) {
            return stream(candidates)
                    .map(document -> new VectorSearchResult(document, cosine(queryEmbedding, document.embedding())))
                    .filter(result -> result.score() > 0)
                    .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                            .thenComparing(result -> chunkOrder(result.document())))
                    .limit(topK)
                    .toList();
        }

        private static boolean matchesObject(VectorDocument document, String objectType, String objectId) {
            Map<String, Object> metadata = document.metadata();
            return Objects.equals(objectType, Objects.toString(metadata.get("objectType"), null))
                    && Objects.equals(objectId, Objects.toString(metadata.get("objectId"), null));
        }

        private static int chunkOrder(VectorDocument document) {
            Object value = document.metadata().get("chunkOrder");
            return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
        }

        private static double lexicalScore(Set<String> queryTerms, String content) {
            if (queryTerms.isEmpty()) {
                return 0;
            }
            Set<String> contentTerms = terms(content);
            long matches = queryTerms.stream().filter(contentTerms::contains).count();
            return matches / (double) queryTerms.size();
        }

        private static java.util.stream.Stream<VectorDocument> stream(Iterable<VectorDocument> candidates) {
            return StreamSupport.stream(candidates.spliterator(), false);
        }
    }

    private static List<Double> vectorize(String text) {
        Set<String> terms = terms(text);
        return List.of(
                containsAny(terms, "업로드", "파일", "첨부") ? 1.0 : 0.0,
                containsAny(terms, "크기", "제한", "50mb", "mb") ? 1.0 : 0.0,
                containsAny(terms, "고객", "지원", "대시보드", "회의") ? 1.0 : 0.0,
                containsAny(terms, "개인정보", "암호화", "감사") ? 1.0 : 0.0,
                containsAny(terms, "보존", "삭제", "승인") ? 1.0 : 0.0);
    }

    private static boolean containsAny(Set<String> terms, String... candidates) {
        for (String candidate : candidates) {
            if (terms.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> terms(String text) {
        return List.of(text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^0-9a-z가-힣]+", " ")
                        .trim()
                        .split("\\s+"))
                .stream()
                .filter(term -> !term.isBlank())
                .collect(Collectors.toSet());
    }

    private static double cosine(List<Double> left, List<Double> right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
