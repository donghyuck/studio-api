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
        var list = controller.search("spring", 10).getBody().getData();

        assertEquals("Spring Boot", response.name());
        assertEquals("spring boot", response.normalizedName());
        assertEquals("backend", response.categoryId());
        assertEquals("ACTIVE", response.status());
        assertEquals(1, list.size());
        assertEquals(response.skillId(), list.get(0).skillId());
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
