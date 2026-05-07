package studio.one.base.user.service;

import java.util.List;

import studio.one.base.user.company.model.CompanyPermissionPolicyRef;
import studio.one.base.user.company.model.CompanyPermissionRolePolicyRef;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyPermissionService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-company-permission-service";

    boolean isGranted(Long companyId, Long userId, String action);

    void assertGranted(Long companyId, Long userId, String action);

    List<String> getGrantedActions(Long companyId, Long userId);

    CompanyPermissionPolicyRef getPolicy(Long companyId);

    CompanyPermissionPolicyRef updatePolicy(Long companyId, List<CompanyPermissionRolePolicyRef> roles, Long actorUserId);
}
