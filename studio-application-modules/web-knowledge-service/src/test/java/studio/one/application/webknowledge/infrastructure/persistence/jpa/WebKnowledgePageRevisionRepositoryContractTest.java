package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class WebKnowledgePageRevisionRepositoryContractTest {

    @Test
    void snapshotQuotaQueryUsesPortableNativeCharacterLength() throws Exception {
        Query query = WebKnowledgePageRevisionJpaRepository.class
                .getMethod("sumCompletedSnapshotUnitsByWorkspaceId", Long.class)
                .getAnnotation(Query.class);

        assertTrue(query.nativeQuery());
        String normalized = query.value().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(normalized.contains("char_length(normalized_snapshot)"));
        assertTrue(normalized.contains("workspace_id = :workspaceid"));
        assertTrue(normalized.contains("status = 'completed'"));
    }
}
