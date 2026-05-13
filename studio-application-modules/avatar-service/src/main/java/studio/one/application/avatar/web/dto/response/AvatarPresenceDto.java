package studio.one.application.avatar.web.dto.response;

import java.time.OffsetDateTime;

public class AvatarPresenceDto {

    private final boolean hasAvatar;
    private final int count;
    private final Long primaryImageId;
    private final OffsetDateTime primaryModifiedDate;

    public AvatarPresenceDto(
            boolean hasAvatar,
            int count,
            Long primaryImageId,
            OffsetDateTime primaryModifiedDate) {
        this.hasAvatar = hasAvatar;
        this.count = count;
        this.primaryImageId = primaryImageId;
        this.primaryModifiedDate = primaryModifiedDate;
    }

    public boolean isHasAvatar() { return hasAvatar; }

    public boolean hasAvatar() { return hasAvatar; }

    public int getCount() { return count; }

    public int count() { return count; }

    public Long getPrimaryImageId() { return primaryImageId; }

    public Long primaryImageId() { return primaryImageId; }

    public OffsetDateTime getPrimaryModifiedDate() { return primaryModifiedDate; }

    public OffsetDateTime primaryModifiedDate() { return primaryModifiedDate; }

}
