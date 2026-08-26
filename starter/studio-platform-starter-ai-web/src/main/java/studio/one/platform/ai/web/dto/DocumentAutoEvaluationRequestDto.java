package studio.one.platform.ai.web.dto;

import java.util.List;

public record DocumentAutoEvaluationRequestDto(
        Integer questionCount,
        List<String> strategies,
        Integer topK,
        Double minScore) {
}
