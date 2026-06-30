package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

public interface RagRetrievalEvaluationStore {

    RagRetrievalEvaluationResponseDto save(RagRetrievalEvaluationResponseDto result);

    Optional<RagRetrievalEvaluationResponseDto> find(String runId);

    List<RagRetrievalEvaluationResponseDto> list();

    List<RagRetrievalEvaluationResponseDto> listByQuestionSet(String questionSetId);
}
