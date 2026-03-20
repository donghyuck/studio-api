package studio.one.platform.objecttype.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValidateUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Min(0) Long sizeBytes
) {
}
