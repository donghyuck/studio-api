package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;

public interface RagRetrievalEvaluationQuestionSetStore {

    RagRetrievalEvaluationQuestionSetDto save(RagRetrievalEvaluationQuestionSetDto questionSet);

    Optional<RagRetrievalEvaluationQuestionSetDto> find(String questionSetId);

    List<RagRetrievalEvaluationQuestionSetDto> list();
}
