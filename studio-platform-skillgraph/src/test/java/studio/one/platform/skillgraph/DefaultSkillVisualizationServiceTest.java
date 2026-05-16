package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.service.DefaultSkillVisualizationService;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.infrastructure.clustering.DistanceThresholdSkillClusterer;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;

class DefaultSkillVisualizationServiceTest {

    @Test
    void generatesAndStoresUmapProjectionWithClusters() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(1.0d, 0.0d, 0.0d));
        dictionaryStore.save(new SkillDictionary("skill-2", "OAuth2", "oauth2", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(0.9d, 0.1d, 0.0d));
        dictionaryStore.save(new SkillDictionary("skill-3", "Docker", "docker", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(0.0d, 1.0d, 0.0d));
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        DefaultSkillVisualizationService service = new DefaultSkillVisualizationService(
                dictionaryStore,
                projectionStore,
                new DistanceThresholdSkillClusterer(0.5d));

        var result = service.generateProjection("projection-1", 100);

        assertEquals("projection-1", result.projectionId());
        assertEquals(3, result.itemCount());
        assertFalse(result.points().isEmpty());
        assertFalse(result.clusters().isEmpty());
        assertEquals(3, service.findProjectionPoints("projection-1", null, 100, 0).size());
    }

    @Test
    void returnsEmptyProjectionWhenNoEmbeddingsExist() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        DefaultSkillVisualizationService service = new DefaultSkillVisualizationService(
                dictionaryStore,
                new InMemorySkillProjectionStore(),
                new DistanceThresholdSkillClusterer());

        var result = service.generateProjection("projection-1", 100);

        assertEquals(0, result.itemCount());
        assertEquals(0, result.clusterCount());
    }
}
