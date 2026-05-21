package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.command.GenerateSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveAndAssignSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillCategoryDraftService;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillCategoryDraftServiceTest {

    @Test
    void generatesDraftsFromProjectionClustersAndSavesCategories() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Security JWT 인증 구성", null, null, "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "OAuth2 로그인 연동", null, null, "ACTIVE", now, now));
        candidateStore.saveCandidate(new SkillCandidate("candidate-1", "chunk-1", null, null,
                "Spring Security JWT 인증 구성", null, SkillCandidateStatus.APPROVED, 0.95d, 7, "skill-1", null,
                now, now));
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now),
                new SkillProjection("default", "skill-2", 0.2d, 0.3d, "cluster-1", 1, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 2, now)));
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore,
                candidateStore);

        var representatives = service.findRepresentatives("default", "cluster-1", false,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("occurrenceCount"))));
        var drafts = service.generateDrafts(new GenerateSkillCategoryDraftCommand(
                "default",
                List.of("cluster-1"),
                5,
                false,
                false));
        var saved = service.saveDrafts(new SaveSkillCategoryDraftCommand(List.of(
                new SaveSkillCategoryDraftCommand.SaveSkillCategoryItem(
                        "auth-security",
                        null,
                        drafts.drafts().get(0).proposedName(),
                        0))));

        assertEquals(1, drafts.draftCount());
        assertEquals("인증·인가 보안", drafts.drafts().get(0).proposedName());
        assertEquals("인증·인가 보안", drafts.drafts().get(0).suggestedCategoryName());
        assertEquals("skill-1", representatives.getContent().get(0).skillId());
        assertEquals(7, representatives.getContent().get(0).occurrenceCount());
        assertEquals(List.of("Spring Security JWT 인증 구성", "OAuth2 로그인 연동"),
                drafts.drafts().get(0).representativeSkillNames());
        assertEquals(2, drafts.drafts().get(0).representativeSkills().size());
        assertEquals("auth-security", saved.get(0).categoryId());
        assertEquals(1, taxonomyStore.findCategories(null).size());
    }

    @Test
    void usesLlmCategoryNameWhenRequested() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "OAuth2 로그인 연동", null, null, "ACTIVE", now, now));
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 1, now)));
        CapturingPromptRenderer promptRenderer = new CapturingPromptRenderer();
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore,
                null,
                promptRenderer,
                request -> new ChatResponse(List.of(ChatMessage.assistant(
                        "{\"suggestedCategoryName\":\"LLM 인증 보안\"}")), "test-model", Map.of()),
                new ObjectMapper());

        var drafts = service.generateDrafts(new GenerateSkillCategoryDraftCommand(
                "default",
                List.of("cluster-1"),
                5,
                false,
                true));

        assertEquals("skill-category-naming", promptRenderer.name);
        assertEquals("LLM 인증 보안", drafts.drafts().get(0).suggestedCategoryName());
    }

    @Test
    void fallsBackToHeuristicNameWhenLlmReturnsMalformedJson() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "OAuth2 로그인 연동", null, null, "ACTIVE", now, now));
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 1, now)));
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore,
                null,
                new CapturingPromptRenderer(),
                request -> new ChatResponse(List.of(ChatMessage.assistant("{\n  suggested")), "test-model", Map.of()),
                new ObjectMapper());

        var drafts = service.generateDrafts(new GenerateSkillCategoryDraftCommand(
                "default",
                List.of("cluster-1"),
                5,
                false,
                true));

        assertEquals("인증·인가 보안", drafts.drafts().get(0).suggestedCategoryName());
    }

    @Test
    void extractsLlmCategoryNameWhenJsonResponseIsTruncatedAfterName() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "멀티테넌트 데이터 보안 취약점 대응", null, null,
                "ACTIVE", now, now));
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 1, now)));
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore,
                null,
                new CapturingPromptRenderer(),
                request -> new ChatResponse(List.of(ChatMessage.assistant(
                        "{\"suggestedCategoryName\": \"멀티테넌트 데이터 관리\", \"confidence\":")),
                        "test-model",
                        Map.of("finishReason", "LENGTH")),
                new ObjectMapper());

        var drafts = service.generateDrafts(new GenerateSkillCategoryDraftCommand(
                "default",
                List.of("cluster-1"),
                5,
                false,
                true));

        assertEquals("멀티테넌트 데이터 관리", drafts.drafts().get(0).suggestedCategoryName());
    }

    @Test
    void savesDraftCategoriesAndAssignsClusterSkills() {
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillProjectionStore projectionStore = new InMemorySkillProjectionStore();
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        Instant now = Instant.now();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Security JWT 인증 구성", null, null,
                "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "OAuth2 로그인 연동", null, null, "ACTIVE", now, now));
        projectionStore.replaceProjection("projection-1", List.of(
                new SkillProjection("projection-1", "skill-1", 0.1d, 0.2d, "cluster-1", 0, now),
                new SkillProjection("projection-1", "skill-2", 0.2d, 0.3d, "cluster-1", 1, now)),
                List.of(new SkillCluster("cluster-1", null, "distance-threshold", 2, now)));
        DefaultSkillCategoryDraftService service = new DefaultSkillCategoryDraftService(
                projectionStore,
                dictionaryStore,
                taxonomyStore,
                candidateStore);

        var result = service.saveAndAssignDrafts(new SaveAndAssignSkillCategoryDraftCommand(
                "projection-1",
                false,
                List.of(new SaveAndAssignSkillCategoryDraftCommand.SaveAndAssignSkillCategoryDraftItem(
                        "cluster-1",
                        "auth-security",
                        null,
                        "인증·인가 보안",
                        1))));

        assertEquals(1, result.savedCategoryCount());
        assertEquals(2, result.assignedSkillCount());
        assertEquals("auth-security", dictionaryStore.findById("skill-1").orElseThrow().categoryId());
        assertEquals("auth-security", dictionaryStore.findById("skill-2").orElseThrow().categoryId());
        assertEquals(3, taxonomyStore.findCategoryHistory("auth-security", PageRequest.of(0, 10)).getTotalElements());
    }

    private static final class CapturingPromptRenderer implements PromptRenderer {
        private String name;

        @Override
        public String render(String name, Map<String, Object> params) {
            this.name = name;
            return "prompt";
        }

        @Override
        public String getRawPrompt(String name) {
            return "prompt";
        }
    }
}
