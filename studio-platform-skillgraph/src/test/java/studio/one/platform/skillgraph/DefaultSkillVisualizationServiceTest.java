package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.service.DefaultSkillVisualizationService;
import studio.one.platform.skillgraph.application.command.GenerateSkillProjectionCommand;
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
        assertEquals(3, service.findProjectionPoints("projection-1", null, PageRequest.of(0, 100))
                .getContent()
                .size());
        var projections = service.listProjections(PageRequest.of(0, 10));
        assertEquals(1, projections.getContent().size());
        assertEquals("projection-1", projections.getContent().get(0).projectionId());
        assertEquals(3, projections.getContent().get(0).itemCount());
        assertEquals(result.clusterCount(), projections.getContent().get(0).clusterCount());
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
        assertEquals(0, service.listProjections(PageRequest.of(0, 10)).getContent().size());
    }

    @Test
    void separatesProjectionBySkillTypeAndStoresClusterMembers() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("task-1", "REST API 구현", "rest api 구현", null, "TASK_SKILL",
                "ACTIVE", now, now), List.of(1.0d, 0.0d, 0.0d));
        dictionaryStore.save(new SkillDictionary("task-2", "JWT 인증 구현", "jwt 인증 구현", null, "TASK_SKILL",
                "ACTIVE", now, now), List.of(0.9d, 0.1d, 0.0d));
        dictionaryStore.save(new SkillDictionary("tech-1", "Spring Boot", "spring boot", null, "TECH_SKILL",
                "ACTIVE", now, now), List.of(0.0d, 1.0d, 0.0d));
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        DefaultSkillVisualizationService service = new DefaultSkillVisualizationService(
                dictionaryStore,
                projectionStore,
                new DistanceThresholdSkillClusterer(0.5d));

        var result = service.generateProjection(new GenerateSkillProjectionCommand(
                "projection-task",
                100,
                "TASK_SKILL",
                "CLUSTERING",
                "PCA",
                2,
                "HDBSCAN",
                null,
                null,
                null,
                "{\"minClusterSize\":2}"));

        assertEquals(2, result.itemCount());
        assertEquals("TASK_SKILL", result.skillType());
        assertEquals("CLUSTERING", result.projectionType());
        var summary = service.listProjections(PageRequest.of(0, 10)).getContent().get(0);
        assertEquals("TASK_SKILL", summary.skillType());
        assertEquals("CLUSTERING", summary.projectionType());
        assertEquals(2, summary.projectionDimension());
        var cluster = service.findClusters("projection-task").get(0);
        assertEquals("TASK_SKILL", cluster.skillType());
        assertFalse(cluster.representativeSkillIds().isEmpty());
        var members = service.findClusterMembers("projection-task", cluster.clusterId(), PageRequest.of(0, 10));
        assertEquals(2, members.getTotalElements());
        assertEquals(2, members.getContent().stream().filter(item -> item.representative()).count());
    }
}
