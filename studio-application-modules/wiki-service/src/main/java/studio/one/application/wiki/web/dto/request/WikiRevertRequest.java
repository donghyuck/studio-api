package studio.one.application.wiki.web.dto.request;

import jakarta.validation.constraints.Positive;

public record WikiRevertRequest(
        @Positive Long baseRevisionId) {
}
