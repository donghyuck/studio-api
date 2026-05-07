package studio.one.base.user.persistence.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import studio.one.base.user.domain.entity.ApplicationCompanyMemberKey;
import studio.one.base.user.persistence.ApplicationCompanyMemberKeyRepository;

@Repository(ApplicationCompanyMemberKeyRepository.SERVICE_NAME)
public interface ApplicationCompanyMemberKeyJpaRepository
        extends JpaRepository<ApplicationCompanyMemberKey, Long>, ApplicationCompanyMemberKeyRepository {

    @Override
    Optional<ApplicationCompanyMemberKey> findByKeyHash(String keyHash);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApplicationCompanyMemberKey k where k.keyId = :keyId")
    Optional<ApplicationCompanyMemberKey> findForUpdateById(@Param("keyId") Long keyId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from ApplicationCompanyMemberKey k where k.keyHash = :keyHash")
    Optional<ApplicationCompanyMemberKey> findForUpdateByKeyHash(@Param("keyHash") String keyHash);
}
