package studio.one.platform.ai.core.vector.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NeighborVectorProjectionGeneratorTest {

    @Test
    void umapGeneratesNormalizedPoints() {
        UmapVectorProjectionGenerator generator = new UmapVectorProjectionGenerator();

        List<VectorProjectionPoint> points = generator.generate("proj-umap", items(), Instant.parse("2026-04-30T00:00:00Z"));

        assertThat(generator.algorithm()).isEqualTo(ProjectionAlgorithm.UMAP);
        assertThat(points).hasSize(4);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.projectionId()).isEqualTo("proj-umap");
            assertThat(point.x()).isBetween(-1.0, 1.0);
            assertThat(point.y()).isBetween(-1.0, 1.0);
        });
    }

    @Test
    void tsneGeneratesNormalizedPoints() {
        TsneVectorProjectionGenerator generator = new TsneVectorProjectionGenerator();

        List<VectorProjectionPoint> points = generator.generate("proj-tsne", items(), Instant.parse("2026-04-30T00:00:00Z"));

        assertThat(generator.algorithm()).isEqualTo(ProjectionAlgorithm.TSNE);
        assertThat(points).hasSize(4);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.projectionId()).isEqualTo("proj-tsne");
            assertThat(point.x()).isBetween(-1.0, 1.0);
            assertThat(point.y()).isBetween(-1.0, 1.0);
        });
    }

    @Test
    void generatorsReturnEmptyWhenNoEmbeddingExists() {
        assertThat(new UmapVectorProjectionGenerator().generate("proj-1", List.of(item("a", List.of())), Instant.now()))
                .isEmpty();
        assertThat(new TsneVectorProjectionGenerator().generate("proj-1", List.of(item("a", List.of())), Instant.now()))
                .isEmpty();
    }

    private List<VectorItem> items() {
        return List.of(
                item("a", List.of(1.0, 0.0, 0.0, 0.2)),
                item("b", List.of(0.9, 0.1, 0.0, 0.3)),
                item("c", List.of(0.0, 1.0, 0.1, 0.1)),
                item("d", List.of(0.0, 0.8, 0.2, 0.0)));
    }

    private static VectorItem item(String id, List<Double> embedding) {
        return new VectorItem(id, "TYPE", "source", id, "text", embedding, "model", embedding.size(), Map.of(), Instant.now());
    }
}
