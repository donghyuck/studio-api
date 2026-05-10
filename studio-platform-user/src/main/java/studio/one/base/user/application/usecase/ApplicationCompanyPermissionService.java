package studio.one.base.user.application.usecase;

import java.util.List;

import studio.one.base.user.domain.model.company.CompanyPermissionPolicyRef;
import studio.one.base.user.domain.model.company.CompanyPermissionRolePolicyRef;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyPermissionService {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:application-company-permission-service";

    boolean isGranted(Long companyId, Long userId, String action);

    void assertGranted(Long companyId, Long userId, String action);

    List<String> getGrantedActions(Long companyId, Long userId);

    CompanyPermissionPolicyRef getPolicy(Long companyId);

    CompanyPermissionPolicyRef updatePolicy(
            Long companyId,
            List<CompanyPermissionRolePolicyRef> roles,
            Long actorUserId,
            boolean platformAdmin);
}
