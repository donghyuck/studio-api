package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.application.service.DuplicateSkillDictionaryException;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;

class DefaultSkillDictionaryServiceTest {

    @Test
    void createsDictionarySkillAndSearchesIt() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());

        var created = service.create(new CreateSkillDictionaryCommand(
                "Spring Boot",
                null,
                "backend",
                null,
                null));
        var searched = service.search("spring", 10);

        assertFalse(created.skillId().isBlank());
        assertEquals("Spring Boot", created.name());
        assertEquals("spring boot", created.normalizedName());
        assertEquals("backend", created.categoryId());
        assertEquals("ACTIVE", created.status());
        assertEquals(1, searched.size());
        assertEquals(created.skillId(), searched.get(0).skillId());
    }

    @Test
    void rejectsDuplicateNormalizedName() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());
        service.create(new CreateSkillDictionaryCommand("Spring Boot", null, null, null, null));

        assertThrows(DuplicateSkillDictionaryException.class,
                () -> service.create(new CreateSkillDictionaryCommand("SpringBoot", "spring boot", null, null, null)));
    }

    @Test
    void usesExplicitNormalizedName() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());

        var created = service.create(new CreateSkillDictionaryCommand(
                "SpringBoot",
                "Spring Boot",
                null,
                "DRAFT",
                null));

        assertEquals("spring boot", created.normalizedName());
        assertEquals("DRAFT", created.status());
    }

    @Test
    void searchesWithOffsetAndLimit() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());
        service.create(new CreateSkillDictionaryCommand("Backend REST API 설계", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Frontend Vue 컴포넌트 개발", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));

        var page = service.search(null, 1, 2);

        assertEquals(2, page.size());
        assertEquals("Frontend Vue 컴포넌트 개발", page.get(0).name());
        assertEquals("Spring Security JWT 인증 구성", page.get(1).name());
    }
}
