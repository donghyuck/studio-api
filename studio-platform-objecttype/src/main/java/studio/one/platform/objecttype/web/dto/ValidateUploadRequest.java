package studio.one.platform.objecttype.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record ValidateUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Min(0) Long sizeBytes
) {
}
