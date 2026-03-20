package studio.one.platform.objecttype.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ObjectTypePatchRequest(
        @Pattern(regexp = "^[a-z][a-z0-9_\\-]{1,79}$") String code,
        String name,
        String domain,
        String status,
        String description,
        @NotBlank String updatedBy,
        @NotNull @Min(1) Long updatedById
) {
}
