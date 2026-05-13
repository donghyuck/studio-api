package studio.one.base.user.infrastructure.persistence.jpa;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.port.ApplicationCompanyJoinRequestRepository;

@Repository(ApplicationCompanyJoinRequestRepository.SERVICE_NAME)
public interface ApplicationCompanyJoinRequestJpaRepository
        extends JpaRepository<ApplicationCompanyJoinRequest, Long>, ApplicationCompanyJoinRequestRepository {

    @Override
    @Query("select r from ApplicationCompanyJoinRequest r\\n" + " where r.companyId = :companyId\\n" + "   and (:status is null or r.status = :status)\\n")
    Page<ApplicationCompanyJoinRequest> findAllByCompanyId(
            @Param("companyId") Long companyId,
            @Param("status") CompanyJoinRequestStatus status,
            Pageable pageable);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApplicationCompanyJoinRequest r where r.requestId = :requestId")
    Optional<ApplicationCompanyJoinRequest> findForUpdateById(@Param("requestId") Long requestId);

    @Override
    @Query("select count(r) > 0 from ApplicationCompanyJoinRequest r\\n" + " where r.keyId = :keyId\\n" + "   and r.userId = :userId\\n" + "   and r.status = studio.one.base.user.domain.model.company.CompanyJoinRequestStatus.PENDING\\n")
    boolean existsPendingByKeyIdAndUserId(@Param("keyId") Long keyId, @Param("userId") Long userId);

    @Override
    @Query("select count(r) > 0 from ApplicationCompanyJoinRequest r\\n" + " where r.companyId = :companyId\\n" + "   and r.userId = :userId\\n" + "   and r.status = studio.one.base.user.domain.model.company.CompanyJoinRequestStatus.PENDING\\n")
    boolean existsPendingByCompanyIdAndUserId(@Param("companyId") Long companyId, @Param("userId") Long userId);

    @Override
    @Query("select count(r) from ApplicationCompanyJoinRequest r\\n" + " where r.keyId = :keyId\\n" + "   and r.status = studio.one.base.user.domain.model.company.CompanyJoinRequestStatus.PENDING\\n" + "   and r.userId is not null\\n")
    long countPendingByKeyId(@Param("keyId") Long keyId);
}
