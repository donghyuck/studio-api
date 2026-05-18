package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillCategoryDraftService;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillCategoryDraftServiceTest {

    @Test
    void generatesDraftsFromProjectionClustersAndSavesCategories() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Security JWT 인증 구성", null, null, "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "OAuth2 로그인 연동", null, null, "ACTIVE", now, now));
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now),
                new SkillProjection("default", "skill-2", 0.2d, 0.3d, "cluster-1", 1, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 2, now)));
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore);

        var drafts = service.generateDrafts("default", 5);
        var saved = service.saveDrafts(new SaveSkillCategoryDraftCommand(List.of(
                new SaveSkillCategoryDraftCommand.SaveSkillCategoryItem(
                        "auth-security",
                        null,
                        drafts.drafts().get(0).proposedName(),
                        0))));

        assertEquals(1, drafts.draftCount());
        assertEquals("인증·인가 보안", drafts.drafts().get(0).proposedName());
        assertEquals(List.of("Spring Security JWT 인증 구성", "OAuth2 로그인 연동"),
                drafts.drafts().get(0).representativeSkillNames());
        assertEquals("auth-security", saved.get(0).categoryId());
        assertEquals(1, taxonomyStore.findCategories(null).size());
    }
}
