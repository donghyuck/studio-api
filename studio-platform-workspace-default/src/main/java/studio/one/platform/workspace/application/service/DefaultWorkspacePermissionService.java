package studio.one.platform.workspace.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.platform.workspace.application.error.WorkspaceNotFoundException;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionContributor;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.domain.model.WorkspaceRolePermissionMapping;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

@RequiredArgsConstructor
public class DefaultWorkspacePermissionService implements WorkspacePermissionService {

    private static final Set<String> READ_ONLY_ACTIONS = Set.of(
            WorkspacePermissionActions.READ,
            WorkspacePermissionActions.TREE_READ,
            WorkspacePermissionActions.MEMBER_READ,
            WorkspacePermissionActions.PERMISSION_READ);

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;
    private final List<WorkspacePermissionContributor> contributors;
    private final WorkspaceSettings settings;
    private final ApplicationCompanyMemberService companyMemberService;
    private final ApplicationCompanyPermissionService companyPermissionService;

    public DefaultWorkspacePermissionService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            List<WorkspacePermissionContributor> contributors,
            WorkspaceSettings settings) {
        this(workspaceRepository, closureRepository, memberRepository, contributors, settings, null, null);
    }

    public DefaultWorkspacePermissionService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            List<WorkspacePermissionContributor> contributors,
            WorkspaceSettings settings,
            ApplicationCompanyMemberService companyMemberService) {
        this(workspaceRepository, closureRepository, memberRepository, contributors, settings, companyMemberService, null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGranted(Long workspaceId, Long userId, String action) {
        return isGranted(workspaceId, new WorkspaceAccessContext(userId, null, false), action);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGranted(Long workspaceId, WorkspaceAccessContext actor, String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        WorkspaceEntity workspace = workspace(workspaceId);
        if (workspace.isArchived() && !isReadOnlyAction(action) && !WorkspacePermissionActions.ACTIVATE.equals(action)) {
            return false;
        }
        if (actor != null && actor.platformAdmin()) {
            return true;
        }
        Long userId = actor == null ? null : actor.userId();
        WorkspaceRole role = getEffectiveWorkspaceRole(workspace, userId);
        if (isMapped(role, action)) {
            return true;
        }
        return hasCompanyOwnerOverride(workspace, userId, action) && isMapped(WorkspaceRole.OWNER, action);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertGranted(Long workspaceId, Long userId, String action) {
        assertGranted(workspaceId, new WorkspaceAccessContext(userId, null, false), action);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertGranted(Long workspaceId, WorkspaceAccessContext actor, String action) {
        if (!isGranted(workspaceId, actor, action)) {
            throw new AccessDeniedException("Workspace permission denied: " + action);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole getEffectiveRole(Long workspaceId, Long userId) {
        return getEffectiveRole(workspace(workspaceId), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRole getEffectiveRole(Long workspaceId, WorkspaceAccessContext actor) {
        if (actor != null && actor.platformAdmin()) {
            return WorkspaceRole.OWNER;
        }
        return getEffectiveRole(workspace(workspaceId), actor == null ? null : actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getGrantedActions(Long workspaceId, WorkspaceAccessContext actor) {
        WorkspaceEntity workspace = workspace(workspaceId);
        Long userId = actor == null ? null : actor.userId();
        WorkspaceRole role = actor != null && actor.platformAdmin()
                ? WorkspaceRole.OWNER
                : getEffectiveWorkspaceRole(workspace, userId);
        boolean companyOwnerOverride = role != WorkspaceRole.OWNER && isCompanyOwner(workspace, userId);
        if (companyOwnerOverride) {
            role = WorkspaceRole.OWNER;
        }
        if (role == null) {
            return List.of();
        }
        Set<String> actions = new LinkedHashSet<>();
        for (WorkspaceRolePermissionMapping mapping : mappings()) {
            if (role == WorkspaceRole.OWNER || role.rank() >= mapping.role().rank()) {
                actions.add(mapping.action());
            }
        }
        if (role == WorkspaceRole.OWNER) {
            getPermissionDefinitions().forEach(def -> actions.add(def.action()));
        }
        return actions.stream()
                .filter(action -> !workspace.isArchived()
                        || isReadOnlyAction(action)
                        || WorkspacePermissionActions.ACTIVATE.equals(action))
                .filter(action -> !companyOwnerOverride || hasCompanyOwnerOverride(workspace, userId, action))
                .sorted()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspacePermissionDefinition> getPermissionDefinitions() {
        Map<String, WorkspacePermissionDefinition> definitions = new LinkedHashMap<>();
        WorkspacePermissionActions.definitions().forEach(def -> definitions.put(def.action(), def));
        for (WorkspacePermissionContributor contributor : contributors) {
            if (contributor.permissions() != null) {
                contributor.permissions().forEach(def -> definitions.putIfAbsent(def.action(), def));
            }
        }
        return new ArrayList<>(definitions.values());
    }

    private WorkspaceRole getEffectiveRole(WorkspaceEntity workspace, Long userId) {
        return getEffectiveWorkspaceRole(workspace, userId);
    }

    private WorkspaceRole getEffectiveWorkspaceRole(WorkspaceEntity workspace, Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        WorkspaceRole strongest = null;
        List<Long> ancestorIds = settings.inheritParentRole()
                ? closureRepository.findAncestorIds(workspace.getWorkspaceId())
                : List.of(workspace.getWorkspaceId());
        if (!ancestorIds.isEmpty()) {
            for (WorkspaceMemberEntity member : memberRepository.findByUserIdAndWorkspaceIdIn(userId, ancestorIds)) {
                strongest = WorkspaceRole.strongest(strongest, member.getRole());
            }
        }
        if (strongest == null && workspace.getCompanyId() == null && workspace.getVisibility() != WorkspaceVisibility.PRIVATE) {
            strongest = WorkspaceRole.VIEWER;
        }
        return strongest;
    }

    private boolean isCompanyOwner(WorkspaceEntity workspace, Long userId) {
        if (companyMemberService == null || workspace.getCompanyId() == null || userId == null || userId <= 0) {
            return false;
        }
        return companyMemberService.getCompanyRole(workspace.getCompanyId(), userId) == CompanyRole.OWNER;
    }

    private boolean hasCompanyOwnerOverride(WorkspaceEntity workspace, Long userId, String workspaceAction) {
        if (!isCompanyOwner(workspace, userId)) {
            return false;
        }
        if (companyPermissionService == null) {
            return true;
        }
        String companyAction = isReadOnlyAction(workspaceAction)
                ? CompanyPermissionActions.WORKSPACE_READ
                : CompanyPermissionActions.WORKSPACE_CREATE;
        return companyPermissionService.isGranted(workspace.getCompanyId(), userId, companyAction);
    }

    private boolean isMapped(WorkspaceRole role, String action) {
        if (role == null) {
            return false;
        }
        if (role == WorkspaceRole.OWNER) {
            return getPermissionDefinitions().stream().anyMatch(def -> action.equals(def.action()));
        }
        return mappings().stream()
                .anyMatch(mapping -> action.equals(mapping.action()) && role.rank() >= mapping.role().rank());
    }

    private boolean isReadOnlyAction(String action) {
        return READ_ONLY_ACTIONS.contains(action) || action.endsWith(".read");
    }

    private List<WorkspaceRolePermissionMapping> mappings() {
        List<WorkspaceRolePermissionMapping> mappings = new ArrayList<>(WorkspacePermissionActions.defaultMappings());
        for (WorkspacePermissionContributor contributor : contributors) {
            if (contributor.defaultMappings() != null) {
                mappings.addAll(contributor.defaultMappings());
            }
        }
        mappings.sort(Comparator.comparing(WorkspaceRolePermissionMapping::action));
        return mappings;
    }

    private WorkspaceEntity workspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found: " + workspaceId));
    }
}
