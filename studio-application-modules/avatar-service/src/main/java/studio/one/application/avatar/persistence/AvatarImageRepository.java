package studio.one.application.avatar.persistence;

import java.util.List;
import java.util.Optional;

import studio.one.application.avatar.domain.entity.AvatarImage;

public interface AvatarImageRepository {

    List<AvatarImage> findByUserIdOrderByCreationDateDesc(Long userId);

    Optional<AvatarImage> findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc(Long userId);

    long countByUserId(Long userId);

    Optional<AvatarImage> findById(Long id);

    <S extends AvatarImage> S save(S avatarImage);

    <S extends AvatarImage> List<S> saveAll(Iterable<S> avatars);

    void delete(AvatarImage avatarImage);
}
