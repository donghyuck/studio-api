package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.web.controller.SkillDictionaryMgmtController;
import studio.one.platform.skillgraph.web.dto.request.CreateSkillDictionaryRequest;

class SkillDictionaryMgmtControllerTest {

    @Test
    void createsDictionarySkill() {
        SkillDictionaryMgmtController controller = controller();

        var response = controller.create(new CreateSkillDictionaryRequest(
                "Spring Boot",
                null,
                "backend",
                null,
                "server framework")).getBody().getData();
        var page = controller.search("spring", 0, 10).getBody().getData();

        assertEquals("Spring Boot", response.name());
        assertEquals("spring boot", response.normalizedName());
        assertEquals("backend", response.categoryId());
        assertEquals("ACTIVE", response.status());
        assertEquals(0, page.offset());
        assertEquals(10, page.limit());
        assertEquals(1, page.returned());
        assertEquals(false, page.hasMore());
        assertEquals(response.skillId(), page.items().get(0).skillId());
    }

    @Test
    void searchesDictionarySkillsWithPaging() {
        SkillDictionaryMgmtController controller = controller();
        controller.create(new CreateSkillDictionaryRequest("Backend REST API 설계", null, null, null, null));
        controller.create(new CreateSkillDictionaryRequest("Frontend Vue 컴포넌트 개발", null, null, null, null));
        controller.create(new CreateSkillDictionaryRequest("Spring Security JWT 인증 구성", null, null, null, null));

        var firstPage = controller.search(null, 0, 2).getBody().getData();
        var secondPage = controller.search(null, 2, 2).getBody().getData();

        assertEquals(2, firstPage.returned());
        assertEquals(true, firstPage.hasMore());
        assertEquals(1, secondPage.returned());
        assertEquals(false, secondPage.hasMore());
        assertEquals(2, secondPage.offset());
    }

    @Test
    void duplicateNormalizedNameReturnsConflict() {
        SkillDictionaryMgmtController controller = controller();
        controller.create(new CreateSkillDictionaryRequest("Spring Boot", null, null, null, null));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(new CreateSkillDictionaryRequest("SpringBoot", "spring boot", null, null, null)));

        assertEquals(409, exception.getStatusCode().value());
    }

    private SkillDictionaryMgmtController controller() {
        return new SkillDictionaryMgmtController(
                new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore()));
    }
}
