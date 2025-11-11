package studio.one.application.avatar.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.application.avatar.domain.entity.AvatarImageData;
import studio.one.application.avatar.persistence.AvatarImageDataRepository;

@Repository
public interface AvatarImageDataJpaRepository extends JpaRepository<AvatarImageData, Long>, AvatarImageDataRepository {

    @Override
    AvatarImageData save(AvatarImageData entity);
    
}
