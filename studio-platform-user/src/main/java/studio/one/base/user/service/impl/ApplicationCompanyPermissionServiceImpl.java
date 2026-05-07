package studio.one.base.user.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.company.model.CompanyPermissionPolicyRef;
import studio.one.base.user.company.model.CompanyPermissionRolePolicyRef;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicyId;
import studio.one.base.user.persistence.ApplicationCompanyPermissionPolicyRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyPermissionService;
import studio.one.platform.exception.NotFoundException;

@Service(ApplicationCompanyPermissionService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationCompanyPermissionServiceImpl implements ApplicationCompanyPermissionService {

    private static final Set<String> VALID_ACTIONS = Set.copyOf(CompanyPermissionActions.definitions());

    private final ApplicationCompanyRepository companyRepository;
    private final ApplicationCompanyMemberService memberService;
    private final ApplicationCompanyPermissionPolicyRepository policyRepository;

    @Override
    public boolean isGranted(Long companyId, Long userId, String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        CompanyRole role = memberService.getCompanyRole(companyId, userId);
        return effectiveActions(companyId, role).contains(action);
    }

    @Override
    public void assertGranted(Long companyId, Long userId, String action) {
        if (!isGranted(companyId, userId, action)) {
            throw new AccessDeniedException("Company permission denied: " + action);
        }
    }

    @Override
    public List<String> getGrantedActions(Long companyId, Long userId) {
        return effectiveActions(companyId, memberService.getCompanyRole(companyId, userId)).stream()
                .sorted()
                .toList();
    }

    @Override
    public CompanyPermissionPolicyRef getPolicy(Long companyId) {
        company(companyId);
        return toPolicyRef(companyId, policyRepository.findAllByCompanyId(companyId));
    }

    @Override
    @Transactional
    public CompanyPermissionPolicyRef updatePolicy(
            Long companyId,
            List<CompanyPermissionRolePolicyRef> roles,
            Long actorUserId) {
        ApplicationCompany company = company(companyId);
        List<CompanyPermissionRolePolicyRef> normalized = normalize(roles);
        policyRepository.deleteAllByCompanyId(companyId);
        for (CompanyPermissionRolePolicyRef rolePolicy : normalized) {
            Set<String> enabled = new HashSet<>(rolePolicy.actions());
            for (String action : CompanyPermissionActions.definitions()) {
                ApplicationCompanyPermissionPolicy policy = new ApplicationCompanyPermissionPolicy();
                policy.setId(new ApplicationCompanyPermissionPolicyId(companyId, rolePolicy.role(), action));
                policy.setCompany(company);
                policy.setEnabled(enabled.contains(action));
                policy.setUpdatedBy(actorUserId);
                policyRepository.save(policy);
            }
        }
        return getPolicy(companyId);
    }

    private Set<String> effectiveActions(Long companyId, CompanyRole role) {
        if (role == null) {
            return Set.of();
        }
        Map<CompanyRole, List<ApplicationCompanyPermissionPolicy>> policies = groupByRole(policyRepository.findAllByCompanyId(companyId));
        List<ApplicationCompanyPermissionPolicy> override = policies.get(role);
        if (override == null || override.isEmpty()) {
            return CompanyPermissionActions.actionsFor(role);
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        override.stream()
                .filter(ApplicationCompanyPermissionPolicy::isEnabled)
                .map(policy -> policy.getId().getAction())
                .sorted()
                .forEach(actions::add);
        return Set.copyOf(actions);
    }

    private CompanyPermissionPolicyRef toPolicyRef(Long companyId, List<ApplicationCompanyPermissionPolicy> policies) {
        Map<CompanyRole, List<ApplicationCompanyPermissionPolicy>> byRole = groupByRole(policies);
        List<CompanyPermissionRolePolicyRef> roles = new ArrayList<>();
        for (CompanyRole role : CompanyRole.values()) {
            List<String> defaultActions = sorted(CompanyPermissionActions.actionsFor(role));
            List<ApplicationCompanyPermissionPolicy> override = byRole.get(role);
            if (override == null || override.isEmpty()) {
                roles.add(new CompanyPermissionRolePolicyRef(role, defaultActions, defaultActions, false));
                continue;
            }
            List<String> actions = override.stream()
                    .filter(ApplicationCompanyPermissionPolicy::isEnabled)
                    .map(policy -> policy.getId().getAction())
                    .sorted()
                    .toList();
            roles.add(new CompanyPermissionRolePolicyRef(role, actions, defaultActions, true));
        }
        return new CompanyPermissionPolicyRef(companyId, roles);
    }

    private List<CompanyPermissionRolePolicyRef> normalize(List<CompanyPermissionRolePolicyRef> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        Set<CompanyRole> seenRoles = new HashSet<>();
        List<CompanyPermissionRolePolicyRef> normalized = new ArrayList<>();
        for (CompanyPermissionRolePolicyRef rolePolicy : roles) {
            if (rolePolicy == null || rolePolicy.role() == null) {
                throw new IllegalArgumentException("role must not be null");
            }
            if (!seenRoles.add(rolePolicy.role())) {
                throw new IllegalArgumentException("duplicate role: " + rolePolicy.role());
            }
            LinkedHashSet<String> actions = new LinkedHashSet<>();
            if (rolePolicy.actions() != null) {
                for (String action : rolePolicy.actions()) {
                    if (!StringUtils.hasText(action)) {
                        throw new IllegalArgumentException("action must not be blank");
                    }
                    if (!VALID_ACTIONS.contains(action)) {
                        throw new IllegalArgumentException("Unknown company permission action: " + action);
                    }
                    actions.add(action);
                }
            }
            normalized.add(new CompanyPermissionRolePolicyRef(
                    rolePolicy.role(),
                    actions.stream().sorted().toList(),
                    sorted(CompanyPermissionActions.actionsFor(rolePolicy.role())),
                    true));
        }
        normalized.sort(Comparator.comparing(CompanyPermissionRolePolicyRef::role));
        return normalized;
    }

    private Map<CompanyRole, List<ApplicationCompanyPermissionPolicy>> groupByRole(List<ApplicationCompanyPermissionPolicy> policies) {
        Map<CompanyRole, List<ApplicationCompanyPermissionPolicy>> byRole = new EnumMap<>(CompanyRole.class);
        if (policies == null) {
            return byRole;
        }
        for (ApplicationCompanyPermissionPolicy policy : policies) {
            if (policy.getId() == null || policy.getId().getRole() == null) {
                continue;
            }
            byRole.computeIfAbsent(policy.getId().getRole(), ignored -> new ArrayList<>()).add(policy);
        }
        return byRole;
    }

    private ApplicationCompany company(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("companyId must be positive");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    private List<String> sorted(Set<String> actions) {
        return actions.stream().sorted().toList();
    }
}
