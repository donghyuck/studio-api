package studio.one.application.avatar.domain.port;

import studio.one.application.avatar.domain.model.AvatarImageData;

public interface AvatarImageDataRepository {

    AvatarImageData save(AvatarImageData data);
}
