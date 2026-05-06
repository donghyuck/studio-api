package studio.one.application.wiki.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WikiPageWriteRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull String markdown,
        @Positive Long baseRevisionId) {
}
