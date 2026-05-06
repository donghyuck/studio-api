package studio.one.base.user.service.impl;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyPermissionService;

@Service(ApplicationCompanyPermissionService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationCompanyPermissionServiceImpl implements ApplicationCompanyPermissionService {

    private final ApplicationCompanyMemberService memberService;

    @Override
    public boolean isGranted(Long companyId, Long userId, String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        return CompanyPermissionActions.actionsFor(memberService.getCompanyRole(companyId, userId)).contains(action);
    }

    @Override
    public void assertGranted(Long companyId, Long userId, String action) {
        if (!isGranted(companyId, userId, action)) {
            throw new AccessDeniedException("Company permission denied: " + action);
        }
    }

    @Override
    public List<String> getGrantedActions(Long companyId, Long userId) {
        return CompanyPermissionActions.actionsFor(memberService.getCompanyRole(companyId, userId)).stream()
                .sorted()
                .toList();
    }
}
