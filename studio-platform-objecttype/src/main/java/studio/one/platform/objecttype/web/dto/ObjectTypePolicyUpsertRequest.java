package studio.one.platform.objecttype.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ObjectTypePolicyUpsertRequest(
        Integer maxFileMb,
        String allowedExt,
        String allowedMime,
        String policyJson,
        @NotBlank String updatedBy,
        @NotNull @Min(1) Long updatedById,
        @NotBlank String createdBy,
        @NotNull @Min(1) Long createdById
) {
}
