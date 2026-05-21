package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.PreviewSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillCategoryRelationService;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelationType;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCategoryRelationStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillCategoryRelationServiceTest {

    @Test
    void previewsCategoryRelationsFromRepresentativeSkills() {
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        DefaultSkillCategoryRelationService service = new DefaultSkillCategoryRelationService(
                taxonomyStore,
                dictionaryStore,
                new InMemorySkillCategoryRelationStore());
        Instant now = Instant.now();
        taxonomyStore.saveCategory(new SkillCategory("backend", null, "백엔드 API", 1));
        taxonomyStore.saveCategory(new SkillCategory("security", "backend", "인증 보안", 2));
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Security JWT 인증 구성",
                "spring security jwt 인증 구성", "security", "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "Spring Security API 권한 검증",
                "spring security api 권한 검증", "backend", "ACTIVE", now, now));

        var graph = service.preview(new PreviewSkillCategoryRelationsCommand(
                List.of("backend", "security"),
                10,
                0.1d,
                false,
                false));

        assertEquals(2, graph.categories().size());
        assertEquals(2, graph.skills().size());
        assertFalse(graph.relations().isEmpty());
        assertEquals(SkillCategoryRelationType.PARENT, graph.relations().get(0).relationType());
    }

    @Test
    void savesAndListsCategoryRelations() {
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        DefaultSkillCategoryRelationService service = new DefaultSkillCategoryRelationService(
                taxonomyStore,
                new InMemorySkillDictionaryStore(),
                new InMemorySkillCategoryRelationStore());
        taxonomyStore.saveCategory(new SkillCategory("backend", null, "Backend", 1));
        taxonomyStore.saveCategory(new SkillCategory("security", null, "Security", 2));

        var saved = service.saveRelations(new SaveSkillCategoryRelationsCommand(List.of(
                new SaveSkillCategoryRelationsCommand.SaveSkillCategoryRelationItem(
                        null,
                        "security",
                        "backend",
                        SkillCategoryRelationType.RELATED,
                        0.72d,
                        0.8d,
                        "인증 보안과 백엔드 API가 함께 사용됩니다."))));

        var listed = service.findRelations(List.of("backend"));

        assertEquals(1, saved.size());
        assertEquals(1, listed.size());
        assertEquals("security", listed.get(0).sourceCategoryId());
    }
}
