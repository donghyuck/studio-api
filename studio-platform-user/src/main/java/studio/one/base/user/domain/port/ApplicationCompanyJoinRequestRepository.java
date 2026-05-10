package studio.one.base.user.domain.port;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyJoinRequestRepository {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:company-join-request-repository";

    Page<ApplicationCompanyJoinRequest> findAllByCompanyId(Long companyId, CompanyJoinRequestStatus status, Pageable pageable);

    Optional<ApplicationCompanyJoinRequest> findById(Long requestId);

    default Optional<ApplicationCompanyJoinRequest> findForUpdateById(Long requestId) {
        return findById(requestId);
    }

    boolean existsPendingByKeyIdAndUserId(Long keyId, Long userId);

    boolean existsPendingByCompanyIdAndUserId(Long companyId, Long userId);

    long countPendingByKeyId(Long keyId);

    ApplicationCompanyJoinRequest save(ApplicationCompanyJoinRequest request);
}
