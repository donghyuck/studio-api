package studio.one.application.wiki.web.dto;

import jakarta.validation.constraints.Positive;

public record WikiRevertRequest(
        @Positive Long baseRevisionId) {
}
