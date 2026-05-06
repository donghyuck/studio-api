package studio.one.base.user.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.company.model.CompanyMemberRef;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyMemberService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-company-member-service";

    CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId);

    CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId);

    void removeMember(Long companyId, Long userId, Long actorUserId);

    CompanyMemberRef getMember(Long companyId, Long userId);

    Page<CompanyMemberRef> getMembers(Long companyId, Pageable pageable);

    List<CompanyMemberRef> getMembers(Long companyId);

    boolean isCompanyMember(Long companyId, Long userId);

    CompanyRole getCompanyRole(Long companyId, Long userId);
}
