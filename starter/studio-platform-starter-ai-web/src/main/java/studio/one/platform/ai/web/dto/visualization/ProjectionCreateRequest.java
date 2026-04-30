package studio.one.platform.ai.web.dto.visualization;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectionCreateRequest(
        @NotBlank @Size(max = 200) String name,
        List<String> targetTypes,
        String algorithm,
        Map<String, Object> filters) {
}
