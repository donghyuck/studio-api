package studio.one.platform.workspace.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.platform.workspace.exception.WorkspaceConflictException;
import studio.one.platform.workspace.exception.WorkspaceNotFoundException;
import studio.one.platform.workspace.exception.WorkspaceValidationException;
import studio.one.platform.workspace.model.WorkspaceMemberRef;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.permission.WorkspacePermissionActions;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceMemberCommand;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;

@RequiredArgsConstructor
public class DefaultWorkspaceMemberService implements WorkspaceMemberService {

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;
    private final WorkspacePermissionService permissionService;

    @Override
    @Transactional
    public WorkspaceMemberRef addMember(Long workspaceId, WorkspaceMemberCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_MANAGE);
        Long userId = requireTargetUser(command.userId());
        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw new WorkspaceConflictException("Workspace member already exists: " + userId);
        }
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(requireRole(command.role()));
        member.setCreatedBy(actor.requireUserId());
        return memberRepository.save(member).toRef(false);
    }

    @Override
    @Transactional
    public WorkspaceMemberRef changeRole(Long workspaceId, WorkspaceMemberCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_MANAGE);
        WorkspaceMemberEntity member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, requireTargetUser(command.userId()))
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace member not found: " + command.userId()));
        member.setRole(requireRole(command.role()));
        return memberRepository.save(member).toRef(false);
    }

    @Override
    @Transactional
    public void removeMember(Long workspaceId, Long userId, WorkspaceAccessContext actor) {
        WorkspaceAccessContext resolved = requireActor(actor);
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, resolved, WorkspacePermissionActions.MEMBER_MANAGE);
        WorkspaceMemberEntity member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, requireTargetUser(userId))
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace member not found: " + userId));
        memberRepository.delete(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberRef> getDirectMembers(Long workspaceId, WorkspaceAccessContext actor) {
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_READ);
        return memberRepository.findByWorkspaceIdOrderByUserIdAsc(workspaceId).stream()
                .map(member -> member.toRef(false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberRef> getEffectiveMembers(Long workspaceId, WorkspaceAccessContext actor) {
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_READ);
        List<Long> ancestorIds = closureRepository.findAncestorIds(workspaceId);
        Map<Long, WorkspaceMemberRef> strongest = new LinkedHashMap<>();
        for (WorkspaceMemberEntity member : memberRepository.findByWorkspaceIdIn(ancestorIds)) {
            WorkspaceMemberRef existing = strongest.get(member.getUserId());
            boolean inherited = !workspaceId.equals(member.getWorkspaceId());
            if (existing == null
                    || member.getRole().rank() > existing.role().rank()
                    || (member.getRole().rank() == existing.role().rank() && existing.inherited() && !inherited)) {
                strongest.put(member.getUserId(), new WorkspaceMemberRef(
                        workspaceId,
                        member.getUserId(),
                        member.getRole(),
                        inherited));
            }
        }
        return strongest.values().stream()
                .sorted((left, right) -> Long.compare(left.userId(), right.userId()))
                .toList();
    }

    private WorkspaceAccessContext requireActor(WorkspaceAccessContext actor) {
        if (actor == null) {
            throw new WorkspaceValidationException("Workspace actor is required");
        }
        actor.requireUserId();
        return actor;
    }

    private void requireWorkspace(Long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException("Workspace not found: " + workspaceId);
        }
    }

    private Long requireTargetUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new WorkspaceValidationException("Workspace member userId is required");
        }
        return userId;
    }

    private WorkspaceRole requireRole(WorkspaceRole role) {
        if (role == null) {
            throw new WorkspaceValidationException("Workspace member role is required");
        }
        return role;
    }
}
