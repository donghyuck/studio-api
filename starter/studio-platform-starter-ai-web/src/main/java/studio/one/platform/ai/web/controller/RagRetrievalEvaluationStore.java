package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

public interface RagRetrievalEvaluationStore {

    RagRetrievalEvaluationResponseDto save(RagRetrievalEvaluationResponseDto result);

    Optional<RagRetrievalEvaluationResponseDto> find(String runId);

    List<RagRetrievalEvaluationResponseDto> list();

    List<RagRetrievalEvaluationResponseDto> listByQuestionSet(String questionSetId);

    default List<RagRetrievalEvaluationResponseDto> listByObject(String objectType, String objectId) {
        return list().stream()
                .filter(result -> objectType == null
                        ? result.objectType() == null
                        : result.objectType() != null && objectType.equalsIgnoreCase(result.objectType()))
                .filter(result -> java.util.Objects.equals(objectId, result.objectId()))
                .toList();
    }
}
