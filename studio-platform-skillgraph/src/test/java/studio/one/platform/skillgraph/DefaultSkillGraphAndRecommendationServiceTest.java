package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.CourseSkillMappingCommand;
import studio.one.platform.skillgraph.application.command.NcsSkillMappingCommand;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.SkillRelationCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillGraphService;
import studio.one.platform.skillgraph.application.service.DefaultSkillMappingService;
import studio.one.platform.skillgraph.application.service.DefaultSkillRecommendationService;
import studio.one.platform.skillgraph.application.service.DefaultSkillTaxonomyService;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillMappingStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillGraphAndRecommendationServiceTest {

    @Test
    void savesTaxonomyAndRelations() {
        DefaultSkillTaxonomyService taxonomyService = new DefaultSkillTaxonomyService(new InMemorySkillTaxonomyStore());
        DefaultSkillGraphService graphService = new DefaultSkillGraphService(new InMemorySkillGraphStore());

        taxonomyService.saveCategory(new SkillCategoryCommand("backend", null, "Backend", 1));
        taxonomyService.saveCategory(new SkillCategoryCommand("security", "backend", "Security", 1));
        graphService.saveRelation(new SkillRelationCommand(null, "spring-boot", "oauth2", SkillRelationType.USED_WITH));

        assertEquals(1, taxonomyService.findCategories(null).size());
        assertEquals(1, taxonomyService.findCategories("backend").size());
        assertEquals(1, graphService.findRelations("spring-boot", SkillRelationType.USED_WITH).size());
    }

    @Test
    void mapsNcsAndRecommendsCoursesByMissingSkills() {
        InMemorySkillMappingStore store = new InMemorySkillMappingStore();
        DefaultSkillMappingService mappingService = new DefaultSkillMappingService(store);
        DefaultSkillRecommendationService recommendationService = new DefaultSkillRecommendationService(store);

        mappingService.saveNcsMapping(new NcsSkillMappingCommand(null, "NCS-001", "oauth2", 1.0d));
        mappingService.saveCourseMapping(new CourseSkillMappingCommand(null, "course-a", "oauth2", 1.0d));
        mappingService.saveCourseMapping(new CourseSkillMappingCommand(null, "course-b", "docker", 0.5d));

        var recommendations = recommendationService.recommendCourses(
                java.util.List.of("oauth2", "docker"),
                java.util.List.of("spring-boot"),
                10);

        assertEquals(1, mappingService.findNcsMappings("NCS-001").size());
        assertFalse(recommendations.isEmpty());
        assertEquals("course-a", recommendations.get(0).courseId());
    }
}
