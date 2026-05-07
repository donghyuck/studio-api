package studio.one.base.user.persistence;

import java.util.List;

import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicy;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyPermissionPolicyRepository {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:company-permission-policy-repository";

    List<ApplicationCompanyPermissionPolicy> findAllByCompanyId(Long companyId);

    void deleteAllByCompanyId(Long companyId);

    ApplicationCompanyPermissionPolicy save(ApplicationCompanyPermissionPolicy policy);
}
