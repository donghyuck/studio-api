package studio.one.application.wiki.web.dto.request;

import jakarta.validation.constraints.Positive;

public record WikiArchiveRequest(
        @Positive Long baseRevisionId) {
}
