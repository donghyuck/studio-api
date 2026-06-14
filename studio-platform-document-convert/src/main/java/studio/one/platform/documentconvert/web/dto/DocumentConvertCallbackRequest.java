package studio.one.platform.documentconvert.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentConvertCallbackRequest(
        @NotBlank String status,
        String resultFileId,
        String errorCode,
        String errorMessage) {
}
