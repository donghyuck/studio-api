package studio.one.platform.objecttype.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
