package studio.one.platform.objecttype.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

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
