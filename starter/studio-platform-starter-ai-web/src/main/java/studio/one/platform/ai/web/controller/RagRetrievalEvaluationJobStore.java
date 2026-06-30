package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationJobDto;

public interface RagRetrievalEvaluationJobStore {

    RagRetrievalEvaluationJobDto save(RagRetrievalEvaluationJobDto job);

    Optional<RagRetrievalEvaluationJobDto> find(String jobId);

    List<RagRetrievalEvaluationJobDto> list();
}
