package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.web.controller.SkillGraphSimulationMgmtController;
import studio.one.platform.skillgraph.web.dto.request.SkillExtractionSimulationRequest;

class SkillGraphSimulationMgmtControllerTest {

    @Test
    void simulationExtractionDoesNotPersistCandidates() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        SkillGraphSimulationMgmtController controller = new SkillGraphSimulationMgmtController(
                new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor()));

        var response = controller.simulateExtraction(new SkillExtractionSimulationRequest(
                null,
                null,
                null,
                "Spring Security를 사용해 인증과 권한 처리를 구현하고, JPA로 회원 데이터를 관리한다."))
                .getBody()
                .getData();

        assertFalse(response.candidates().isEmpty());
        assertEquals(0, store.sourceChunks().size());
        assertEquals(0, store.searchCandidates(null, null, 100).size());
        assertEquals(null, response.sourceChunkId());
    }
}
