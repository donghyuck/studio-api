package studio.one.application.avatar.domain.repository; 

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImage;

@Repository
public interface AvatarImageRepository extends JpaRepository<AvatarImage, Long> {

    List<AvatarImage> findByUserIdOrderByCreationDateDesc(Long userId);

    Optional<AvatarImage> findFirstByUserIdAndPrimaryImageTrueOrderByCreationDateDesc(Long userId);

    long countByUserId(Long userId);

}