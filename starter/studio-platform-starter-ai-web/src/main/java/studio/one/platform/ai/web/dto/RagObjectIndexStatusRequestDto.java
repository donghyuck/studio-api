package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record RagObjectIndexStatusRequestDto(
        @NotBlank @Size(max = 80) String objectType,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 80) String> objectIds) {
}
