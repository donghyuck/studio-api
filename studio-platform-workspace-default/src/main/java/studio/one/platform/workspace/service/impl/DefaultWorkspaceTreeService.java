package studio.one.platform.workspace.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.workspace.exception.WorkspaceConflictException;
import studio.one.platform.workspace.exception.WorkspaceNotFoundException;
import studio.one.platform.workspace.exception.WorkspaceValidationException;
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.model.WorkspaceTreeNode;
import studio.one.platform.workspace.model.WorkspaceVisibility;
import studio.one.platform.workspace.permission.WorkspacePermissionActions;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.service.CreateWorkspaceCommand;
import studio.one.platform.workspace.service.UpdateWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;

@RequiredArgsConstructor
public class DefaultWorkspaceTreeService implements WorkspaceTreeService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;
    private final WorkspacePermissionService permissionService;
    private final WorkspaceSettings settings;

    @Override
    @Transactional
    public WorkspaceRef createRoot(CreateWorkspaceCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        String slug = normalizeSlug(command.slug());
        String name = normalizeName(command.name());
        if (workspaceRepository.existsByParentIdIsNullAndSlug(slug) || workspaceRepository.existsByPath(slug)) {
            throw new WorkspaceConflictException("Duplicate root workspace slug: " + slug);
        }
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setName(name);
        entity.setSlug(slug);
        entity.setPath(slug);
        entity.setDepth(0);
        entity.setPosition(0);
        entity.setVisibility(defaultVisibility(command.visibility()));
        entity.setCreatedBy(actor.requireUserId());
        entity.setUpdatedBy(actor.requireUserId());
        entity = workspaceRepository.save(entity);
        entity.setRootId(entity.getWorkspaceId());
        entity = workspaceRepository.save(entity);
        closureRepository.save(new WorkspaceClosureEntity(entity.getWorkspaceId(), entity.getWorkspaceId(), 0));
        addOwner(entity.getWorkspaceId(), actor.requireUserId());
        return entity.toRef();
    }

    @Override
    @Transactional
    public WorkspaceRef createChild(Long parentWorkspaceId, CreateWorkspaceCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        permissionService.assertGranted(parentWorkspaceId, actor, WorkspacePermissionActions.CREATE);
        WorkspaceEntity parent = workspace(parentWorkspaceId);
        if (parent.isArchived()) {
            throw new WorkspaceValidationException("Archived workspace cannot have children");
        }
        if (parent.getDepth() + 1 > settings.maxDepth()) {
            throw new WorkspaceValidationException("Workspace max depth exceeded");
        }
        if (workspaceRepository.countByParentId(parentWorkspaceId) >= settings.maxChildrenPerNode()) {
            throw new WorkspaceValidationException("Workspace max children per node exceeded");
        }
        String slug = normalizeSlug(command.slug());
        if (workspaceRepository.existsByParentIdAndSlug(parentWorkspaceId, slug)) {
            throw new WorkspaceConflictException("Duplicate child workspace slug: " + slug);
        }
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setParentId(parentWorkspaceId);
        entity.setRootId(parent.getRootId());
        entity.setName(normalizeName(command.name()));
        entity.setSlug(slug);
        entity.setPath(parent.getPath() + "/" + slug);
        entity.setDepth(parent.getDepth() + 1);
        entity.setPosition((int) workspaceRepository.countByParentId(parentWorkspaceId));
        entity.setVisibility(defaultVisibility(command.visibility()));
        entity.setCreatedBy(actor.requireUserId());
        entity.setUpdatedBy(actor.requireUserId());
        entity = workspaceRepository.save(entity);
        for (WorkspaceClosureEntity ancestor : closureRepository.findByIdDescendantIdOrderByDepthDesc(parentWorkspaceId)) {
            closureRepository.save(new WorkspaceClosureEntity(
                    ancestor.ancestorId(),
                    entity.getWorkspaceId(),
                    ancestor.getDepth() + 1));
        }
        closureRepository.save(new WorkspaceClosureEntity(entity.getWorkspaceId(), entity.getWorkspaceId(), 0));
        return entity.toRef();
    }

    @Override
    @Transactional
    public WorkspaceRef update(Long workspaceId, UpdateWorkspaceCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.UPDATE);
        WorkspaceEntity entity = workspace(workspaceId);
        rejectArchivedMutation(entity);
        if (command.name() != null) {
            entity.setName(normalizeName(command.name()));
        }
        if (command.visibility() != null) {
            entity.setVisibility(command.visibility());
        }
        entity.setUpdatedBy(actor.requireUserId());
        return workspaceRepository.save(entity).toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRef getById(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.READ);
        return workspace(workspaceId).toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRef getByPath(String path, WorkspaceAccessContext actor) {
        WorkspaceEntity entity = workspaceByPath(path);
        permissionService.assertGranted(entity.getWorkspaceId(), actor, WorkspacePermissionActions.READ);
        return entity.toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceRef> getChildren(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.TREE_READ);
        return workspaceRepository.findByParentIdOrderByPositionAscWorkspaceIdAsc(workspaceId).stream()
                .filter(entity -> isReadable(entity, actor))
                .map(WorkspaceEntity::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceRef> getAncestors(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.TREE_READ);
        Map<Long, WorkspaceEntity> byId = byId(closureRepository.findAncestorIds(workspaceId));
        return closureRepository.findByIdDescendantIdOrderByDepthDesc(workspaceId).stream()
                .filter(closure -> closure.getDepth() > 0)
                .map(closure -> byId.get(closure.ancestorId()))
                .filter(entity -> entity != null)
                .filter(entity -> isReadable(entity, actor))
                .map(WorkspaceEntity::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceRef> getDescendants(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.TREE_READ);
        Map<Long, WorkspaceEntity> byId = byId(closureRepository.findDescendantIds(workspaceId));
        List<WorkspaceEntity> descendants = closureRepository.findByIdAncestorIdOrderByDepthAsc(workspaceId).stream()
                .filter(closure -> closure.getDepth() > 0)
                .map(closure -> byId.get(closure.descendantId()))
                .filter(entity -> entity != null)
                .sorted(Comparator.comparing(WorkspaceEntity::getDepth).thenComparing(WorkspaceEntity::getPath))
                .toList();
        Set<Long> visibleBranchIds = new HashSet<>();
        visibleBranchIds.add(workspaceId);
        List<WorkspaceEntity> visible = new ArrayList<>();
        for (WorkspaceEntity entity : descendants) {
            if (visibleBranchIds.contains(entity.getParentId()) && isReadable(entity, actor)) {
                visibleBranchIds.add(entity.getWorkspaceId());
                visible.add(entity);
            }
        }
        return visible.stream()
                .sorted(Comparator.comparing(WorkspaceEntity::getPath))
                .map(WorkspaceEntity::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceTreeNode getTree(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.TREE_READ);
        WorkspaceEntity root = workspace(workspaceId);
        List<WorkspaceEntity> entities = closureRepository.findDescendantIds(workspaceId).stream()
                .map(id -> workspaceRepository.findById(id).orElse(null))
                .filter(entity -> entity != null)
                .filter(entity -> entity.getWorkspaceId().equals(workspaceId) || isReadable(entity, actor))
                .sorted(Comparator.comparing(WorkspaceEntity::getDepth).thenComparing(WorkspaceEntity::getPosition))
                .toList();
        Map<Long, WorkspaceTreeNodeBuilder> builders = new LinkedHashMap<>();
        for (WorkspaceEntity entity : entities) {
            builders.put(entity.getWorkspaceId(), new WorkspaceTreeNodeBuilder(entity.toRef()));
        }
        for (WorkspaceEntity entity : entities) {
            if (!entity.getWorkspaceId().equals(workspaceId) && builders.containsKey(entity.getParentId())) {
                builders.get(entity.getParentId()).children.put(entity.getWorkspaceId(), builders.get(entity.getWorkspaceId()));
            }
        }
        return builders.get(root.getWorkspaceId()).build();
    }

    @Override
    @Transactional
    public void archive(Long workspaceId, WorkspaceAccessContext actor) {
        WorkspaceAccessContext resolved = requireActor(actor);
        permissionService.assertGranted(workspaceId, resolved, WorkspacePermissionActions.ARCHIVE);
        WorkspaceEntity entity = workspace(workspaceId);
        if (entity.isArchived()) {
            return;
        }
        entity.setArchived(true);
        entity.setArchivedAt(Instant.now());
        entity.setArchivedBy(resolved.requireUserId());
        entity.setUpdatedBy(resolved.requireUserId());
        workspaceRepository.save(entity);
    }

    private WorkspaceAccessContext requireActor(WorkspaceAccessContext actor) {
        if (actor == null) {
            throw new WorkspaceValidationException("Workspace actor is required");
        }
        actor.requireUserId();
        return actor;
    }

    private void addOwner(Long workspaceId, Long userId) {
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(WorkspaceRole.OWNER);
        member.setCreatedBy(userId);
        memberRepository.save(member);
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new WorkspaceValidationException("Workspace name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw new WorkspaceValidationException("Workspace name is too long");
        }
        return trimmed;
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new WorkspaceValidationException("Workspace slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > settings.slugMaxLength() || !SLUG.matcher(normalized).matches()) {
            throw new WorkspaceValidationException("Workspace slug is invalid");
        }
        return normalized;
    }

    private WorkspaceVisibility defaultVisibility(WorkspaceVisibility visibility) {
        return visibility == null ? WorkspaceVisibility.PRIVATE : visibility;
    }

    private void rejectArchivedMutation(WorkspaceEntity entity) {
        if (entity.isArchived()) {
            throw new WorkspaceValidationException("Archived workspace cannot be mutated");
        }
    }

    private WorkspaceEntity workspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found: " + workspaceId));
    }

    private WorkspaceEntity workspaceByPath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new WorkspaceValidationException("Workspace path is required");
        }
        return workspaceRepository.findByPath(path.trim())
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found by path: " + path));
    }

    private Map<Long, WorkspaceEntity> byId(List<Long> ids) {
        Map<Long, WorkspaceEntity> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        workspaceRepository.findByWorkspaceIdIn(ids).forEach(entity -> result.put(entity.getWorkspaceId(), entity));
        return result;
    }

    private boolean isReadable(WorkspaceEntity entity, WorkspaceAccessContext actor) {
        return permissionService.isGranted(entity.getWorkspaceId(), actor, WorkspacePermissionActions.READ);
    }

    private static final class WorkspaceTreeNodeBuilder {
        private final WorkspaceRef ref;
        private final Map<Long, WorkspaceTreeNodeBuilder> children = new LinkedHashMap<>();

        private WorkspaceTreeNodeBuilder(WorkspaceRef ref) {
            this.ref = ref;
        }

        private WorkspaceTreeNode build() {
            return new WorkspaceTreeNode(ref, children.values().stream().map(WorkspaceTreeNodeBuilder::build).toList());
        }
    }
}
