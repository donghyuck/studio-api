package studio.one.application.avatar.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.persistence.AvatarImageRepository;

@Repository
public interface AvatarImageJpaRepository extends JpaRepository<AvatarImage, Long>, AvatarImageRepository {

    @Override
    List<AvatarImage> findByUserIdOrderByCreationDateDesc(Long userId);

    @Override
    Optional<AvatarImage> findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc(Long userId);

    @Override
    long countByUserId(Long userId);

}
