package studio.one.application.avatar.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.model.AvatarImage;
import studio.one.application.avatar.domain.port.AvatarImageRepository;

@Repository
public interface AvatarImageJpaRepository extends JpaRepository<AvatarImage, Long>, AvatarImageRepository {
}
