package studio.one.application.avatar.web.dto.response;

import java.time.OffsetDateTime;

public record AvatarPresenceDto(
        boolean hasAvatar,
        int count,
        Long primaryImageId,
        OffsetDateTime primaryModifiedDate) {
}
