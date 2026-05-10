package studio.one.base.user.application.usecase;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyRef;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyJoinRequestService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-company-join-request-service";

    default CompanyMemberKeyRef createMemberKey(Long companyId, CompanyRole role, Instant expiresAt, Integer maxUses, Long actorUserId) {
        return createMemberKey(companyId, role, expiresAt, maxUses, actorUserId, false);
    }

    CompanyMemberKeyRef createMemberKey(Long companyId, CompanyRole role, Instant expiresAt, Integer maxUses, Long actorUserId, boolean bypassRoleLimit);

    CompanyJoinRequestRef createSelfRequest(String memberKey, Long userId, String name, String email, String message);

    Page<CompanyJoinRequestRef> getRequests(Long companyId, CompanyJoinRequestStatus status, Pageable pageable);

    default CompanyJoinRequestRef approve(Long companyId, Long requestId, Long actorUserId) {
        return approve(companyId, requestId, actorUserId, false);
    }

    CompanyJoinRequestRef approve(Long companyId, Long requestId, Long actorUserId, boolean bypassRoleLimit);

    CompanyJoinRequestRef reject(Long companyId, Long requestId, Long actorUserId);
}
