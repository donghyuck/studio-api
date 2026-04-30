package studio.one.platform.ai.core.vector.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PcaVectorProjectionGeneratorTest {

    private final PcaVectorProjectionGenerator generator = new PcaVectorProjectionGenerator();

    @Test
    void generateProjectsEmbeddingsToNormalizedPoints() {
        List<VectorProjectionPoint> points = generator.generate("proj-1", List.of(
                item("a", List.of(1.0, 0.0, 0.0)),
                item("b", List.of(0.0, 1.0, 0.0)),
                item("c", List.of(0.0, 0.0, 1.0))), Instant.parse("2026-04-30T00:00:00Z"));

        assertThat(points).hasSize(3);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.projectionId()).isEqualTo("proj-1");
            assertThat(point.x()).isBetween(-1.0, 1.0);
            assertThat(point.y()).isBetween(-1.0, 1.0);
        });
    }

    @Test
    void generateReturnsEmptyWhenNoEmbeddingExists() {
        assertThat(generator.generate("proj-1", List.of(item("a", List.of())), Instant.now())).isEmpty();
    }

    private VectorItem item(String id, List<Double> embedding) {
        return new VectorItem(id, "TYPE", "source", id, "text", embedding, "model", embedding.size(), Map.of(), Instant.now());
    }
}
