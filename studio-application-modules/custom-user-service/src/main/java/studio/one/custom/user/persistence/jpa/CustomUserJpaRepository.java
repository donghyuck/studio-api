package studio.one.custom.user.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.custom.user.domain.entity.CustomUser;

/**
 * CustomUser용 JPA 리포지토리. 기본 ApplicationUserRepository를 대체한다.
 */
@Repository
public interface CustomUserJpaRepository extends JpaRepository<CustomUser, Long>, ApplicationUserRepository {
}
