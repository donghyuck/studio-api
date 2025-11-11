package studio.one.application.avatar.persistence;

import studio.one.application.avatar.domain.entity.AvatarImageData;

public interface AvatarImageDataRepository {

    AvatarImageData save(AvatarImageData data);
}
