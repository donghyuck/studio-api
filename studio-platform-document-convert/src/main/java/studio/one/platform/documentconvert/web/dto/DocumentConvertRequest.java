package studio.one.platform.documentconvert.web.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record DocumentConvertRequest(
        @NotBlank String sourceFileId,
        @NotBlank String sourceFormat,
        @NotBlank String targetFormat,
        Map<String, Object> options) {
}
