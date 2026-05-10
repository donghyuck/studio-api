package studio.one.base.user.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.model.company.CompanyMemberRef;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyMemberService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-company-member-service";

    default CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
        return addMember(companyId, userId, role, actorUserId, false);
    }

    CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit);

    default CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
        return changeRole(companyId, userId, role, actorUserId, false);
    }

    CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit);

    default void removeMember(Long companyId, Long userId, Long actorUserId) {
        removeMember(companyId, userId, actorUserId, false);
    }

    void removeMember(Long companyId, Long userId, Long actorUserId, boolean bypassRoleLimit);

    CompanyMemberRef getMember(Long companyId, Long userId);

    Page<CompanyMemberRef> getMembers(Long companyId, Pageable pageable);

    List<CompanyMemberRef> getMembers(Long companyId);

    boolean isCompanyMember(Long companyId, Long userId);

    CompanyRole getCompanyRole(Long companyId, Long userId);
}
