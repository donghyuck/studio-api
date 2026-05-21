package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import studio.one.platform.skillgraph.application.command.AssignCategoryFromClusterCommand;
import studio.one.platform.skillgraph.application.command.AssignSkillsToCategoryCommand;
import studio.one.platform.skillgraph.application.command.MergeSkillCategoriesCommand;
import studio.one.platform.skillgraph.application.command.MoveSkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillTaxonomyService;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillTaxonomyServiceTest {

    @Test
    void searchesCategoriesWithPagingAndMovesWithoutCycle() {
        DefaultSkillTaxonomyService service = service();
        service.saveCategory(new SkillCategoryCommand("backend", null, "Backend", 1));
        service.saveCategory(new SkillCategoryCommand("security", "backend", "Security", 2));
        service.saveCategory(new SkillCategoryCommand("frontend", null, "Frontend", 3));

        var page = service.searchCategories("end", null, PageRequest.of(0, 10));
        var moved = service.moveCategory("security", new MoveSkillCategoryCommand(null, 4));

        assertEquals(2, page.getTotalElements());
        assertEquals("security", moved.categoryId());
        assertEquals(null, moved.parentCategoryId());
        assertThrows(IllegalArgumentException.class,
                () -> service.moveCategory("backend", new MoveSkillCategoryCommand("backend", 1)));
    }

    @Test
    void assignsSkillsDirectlyAndFromCluster() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        DefaultSkillTaxonomyService service = new DefaultSkillTaxonomyService(
                taxonomyStore,
                dictionaryStore,
                projectionStore);
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Security JWT 인증 구성", "spring security jwt 인증 구성",
                null, "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "PostgreSQL 벡터 검색 구현", "postgresql 벡터 검색 구현",
                null, "ACTIVE", now, now));
        projectionStore.replaceProjection("projection-1", List.of(
                new SkillProjection("projection-1", "skill-2", 0.1d, 0.2d, "cluster-1", 0, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 1, now)));
        service.saveCategory(new SkillCategoryCommand("backend", null, "Backend", 1));

        var direct = service.assignSkills("backend", new AssignSkillsToCategoryCommand(List.of("skill-1")));
        var cluster = service.assignFromCluster("backend",
                new AssignCategoryFromClusterCommand("projection-1", "cluster-1", false));

        assertEquals(1, direct.affectedCount());
        assertEquals(1, cluster.affectedCount());
        assertEquals("backend", dictionaryStore.findById("skill-1").orElseThrow().categoryId());
        assertEquals("backend", dictionaryStore.findById("skill-2").orElseThrow().categoryId());
        assertEquals(3, service.findCategoryHistory("backend", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void mergesCategoriesIntoTarget() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        DefaultSkillTaxonomyService service = new DefaultSkillTaxonomyService(taxonomyStore, dictionaryStore, null);
        Instant now = Instant.now();
        service.saveCategory(new SkillCategoryCommand("backend", null, "Backend", 1));
        service.saveCategory(new SkillCategoryCommand("server", null, "Server", 2));
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot API 개발", "spring boot api 개발",
                "server", "ACTIVE", now, now));

        var result = service.mergeCategories(new MergeSkillCategoriesCommand(List.of("server"), "backend", true));

        assertEquals(1, result.affectedCount());
        assertEquals("backend", dictionaryStore.findById("skill-1").orElseThrow().categoryId());
        assertThrows(IllegalArgumentException.class, () -> service.getCategory("server"));
    }

    private DefaultSkillTaxonomyService service() {
        return new DefaultSkillTaxonomyService(
                new InMemorySkillTaxonomyStore(),
                new InMemorySkillDictionaryStore(),
                new InMemorySkillProjectionStore());
    }
}
