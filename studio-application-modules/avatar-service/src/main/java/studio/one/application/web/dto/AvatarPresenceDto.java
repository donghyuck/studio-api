package studio.one.application.web.dto;

import java.time.OffsetDateTime;

public record AvatarPresenceDto(
        boolean hasAvatar,
        int count,
        Long primaryImageId,
        OffsetDateTime primaryModifiedDate) {
}
