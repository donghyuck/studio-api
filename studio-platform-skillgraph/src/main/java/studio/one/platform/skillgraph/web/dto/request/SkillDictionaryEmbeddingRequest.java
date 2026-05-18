package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SkillDictionaryEmbeddingRequest(
        @Min(1) @Max(2000) Integer limit) {
}
