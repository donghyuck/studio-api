package studio.one.application.avatar.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImageData;

@Repository
public interface AvatarImageDataRepository extends JpaRepository<AvatarImageData, Long> {
}
