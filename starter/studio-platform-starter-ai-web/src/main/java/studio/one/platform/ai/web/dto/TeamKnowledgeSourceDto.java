package studio.one.platform.ai.web.dto;

import java.util.Set;

public record TeamKnowledgeSourceDto(
        Long teamId,
        Long workspaceId,
        String sourceType,
        String sourceId,
        String objectId,
        String revisionId,
        Set<String> partitionIds) {

    public TeamKnowledgeSourceDto {
        partitionIds = partitionIds == null ? Set.of() : Set.copyOf(partitionIds);
    }
}
