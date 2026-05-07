package studio.one.base.user.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.base.user.domain.entity.ApplicationCompanyMember;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberId;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyMemberRepository {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:company-member-repository";

    Page<ApplicationCompanyMember> findAllByCompanyId(Long companyId, Pageable pageable);

    List<ApplicationCompanyMember> findAllByCompanyId(Long companyId);

    Optional<ApplicationCompanyMember> findById(ApplicationCompanyMemberId id);

    boolean existsById(ApplicationCompanyMemberId id);

    long countByCompanyIdAndRole(Long companyId, CompanyRole role);

    ApplicationCompanyMember save(ApplicationCompanyMember member);

    void deleteById(ApplicationCompanyMemberId id);
}
