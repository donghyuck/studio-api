package studio.one.application.avatar.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.persistence.AvatarImageRepository;

@Repository
public interface AvatarImageJpaRepository extends JpaRepository<AvatarImage, Long>, AvatarImageRepository {
}
