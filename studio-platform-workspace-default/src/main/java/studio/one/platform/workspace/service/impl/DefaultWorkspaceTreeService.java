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

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.service.ApplicationCompanyService;
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
import studio.one.platform.workspace.service.ChangeWorkspaceParentCommand;
import studio.one.platform.workspace.service.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.service.CreateWorkspaceCommand;
import studio.one.platform.workspace.service.UpdateWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceListQuery;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;

@RequiredArgsConstructor
public class DefaultWorkspaceTreeService implements WorkspaceTreeService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Sort DEFAULT_LIST_SORT = Sort.by("path").ascending();
    private static final Map<String, String> LIST_SORT_PROPERTIES = Map.ofEntries(
            Map.entry("id", "workspaceId"),
            Map.entry("workspaceId", "workspaceId"),
            Map.entry("parentId", "parentId"),
            Map.entry("rootId", "rootId"),
            Map.entry("name", "name"),
            Map.entry("slug", "slug"),
            Map.entry("path", "path"),
            Map.entry("depth", "depth"),
            Map.entry("visibility", "visibility"),
            Map.entry("archived", "archived"));

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;
    private final WorkspacePermissionService permissionService;
    private final WorkspaceSettings settings;
    private final ApplicationCompanyService companyService;

    public DefaultWorkspaceTreeService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            WorkspaceSettings settings) {
        this(workspaceRepository, closureRepository, memberRepository, permissionService, settings, null);
    }

    @Override
    @Transactional
    public WorkspaceRef createRoot(CreateWorkspaceCommand command) {
        return createRoot(CreateRootWorkspaceCommand.from(command));
    }

    @Override
    @Transactional
    public WorkspaceRef createRoot(CreateRootWorkspaceCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        Long companyId = normalizeCompanyId(command.companyId());
        if (requiresCompanyId() && companyId == null) {
            throw new WorkspaceValidationException("Workspace companyId is required");
        }
        validateCompanyExists(companyId);
        String slug = normalizeSlug(command.slug());
        String name = normalizeName(command.name());
        if (existsByScopedRootSlug(companyId, slug) || existsByScopedPath(companyId, slug)) {
            throw new WorkspaceConflictException("Duplicate root workspace slug: " + slug);
        }
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setCompanyId(companyId);
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
        entity.setCompanyId(parent.getCompanyId());
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
    @Transactional
    public WorkspaceRef changeParent(Long workspaceId, ChangeWorkspaceParentCommand command) {
        WorkspaceAccessContext actor = requireActor(command.actor());
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.UPDATE);
        WorkspaceEntity entity = workspace(workspaceId);
        rejectArchivedMutation(entity);

        Long newParentId = command.newParentId();
        if (sameParent(entity.getParentId(), newParentId)) {
            return entity.toRef();
        }

        List<WorkspaceEntity> subtree = new ArrayList<>();
        Map<Long, Integer> relativeDepths = new HashMap<>();
        collectSubtree(entity, 0, subtree, relativeDepths, new HashSet<>());
        Set<Long> subtreeIds = new HashSet<>();
        for (WorkspaceEntity descendant : subtree) {
            subtreeIds.add(descendant.getWorkspaceId());
        }
        if (newParentId != null && subtreeIds.contains(newParentId)) {
            throw new WorkspaceConflictException("Workspace cannot be moved under itself or its descendant");
        }

        WorkspaceEntity newParent = null;
        if (newParentId != null) {
            newParent = workspace(newParentId);
            permissionService.assertGranted(newParentId, actor, WorkspacePermissionActions.CREATE);
            rejectArchivedMutation(newParent);
            if (workspaceRepository.countByParentId(newParentId) >= settings.maxChildrenPerNode()) {
                throw new WorkspaceValidationException("Workspace max children per node exceeded");
            }
            if (workspaceRepository.existsByParentIdAndSlug(newParentId, entity.getSlug())) {
                throw new WorkspaceConflictException("Duplicate child workspace slug: " + entity.getSlug());
            }
            if (!sameCompany(entity.getCompanyId(), newParent.getCompanyId())) {
                throw new WorkspaceConflictException("Workspace cannot be moved across companies");
            }
        } else if (existsByScopedRootSlug(entity.getCompanyId(), entity.getSlug())) {
            throw new WorkspaceConflictException("Duplicate root workspace slug: " + entity.getSlug());
        } else if (requiresCompanyId() && entity.getCompanyId() == null) {
            throw new WorkspaceValidationException("Workspace companyId is required");
        }

        int newDepth = newParent == null ? 0 : newParent.getDepth() + 1;
        int maxRelativeDepth = relativeDepths.values().stream().max(Integer::compareTo).orElse(0);
        if (newDepth + maxRelativeDepth > settings.maxDepth()) {
            throw new WorkspaceValidationException("Workspace max depth exceeded");
        }

        String oldBasePath = entity.getPath();
        String newBasePath = newParent == null ? entity.getSlug() : newParent.getPath() + "/" + entity.getSlug();
        Long newRootId = newParent == null ? entity.getWorkspaceId() : newParent.getRootId();
        int newPosition = newParent == null ? 0 : (int) workspaceRepository.countByParentId(newParentId);

        Long updatedBy = actor.requireUserId();
        for (WorkspaceEntity descendant : subtree) {
            String suffix = descendant.getPath().substring(oldBasePath.length());
            descendant.setRootId(newRootId);
            descendant.setPath(newBasePath + suffix);
            descendant.setDepth(newDepth + relativeDepths.get(descendant.getWorkspaceId()));
            descendant.setUpdatedBy(updatedBy);
            if (descendant.getWorkspaceId().equals(workspaceId)) {
                descendant.setParentId(newParentId);
                descendant.setPosition(newPosition);
            }
        }
        workspaceRepository.saveAll(subtree);

        List<WorkspaceAncestor> newParentAncestors = newParent == null
                ? List.of()
                : collectAncestorDistances(newParent);
        closureRepository.deleteByDescendantIds(subtreeIds);
        List<WorkspaceClosureEntity> rebuiltClosures = new ArrayList<>();
        rebuiltClosures.addAll(rebuildAncestorClosureRows(newParentAncestors));
        rebuiltClosures.addAll(rebuildClosureRows(subtree, relativeDepths, newParentAncestors));
        closureRepository.saveAll(rebuiltClosures);
        return entity.toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRef getById(Long workspaceId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.READ);
        return workspace(workspaceId).toRef();
    }

    @Override
    @Deprecated(since = "2.x", forRemoval = false)
    @Transactional(readOnly = true)
    public WorkspaceRef getByPath(String path, WorkspaceAccessContext actor) {
        if (requiresCompanyId()) {
            throw new WorkspaceValidationException("Workspace companyId is required");
        }
        WorkspaceEntity entity = workspaceByPath(path);
        permissionService.assertGranted(entity.getWorkspaceId(), actor, WorkspacePermissionActions.READ);
        return entity.toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceRef getByPath(Long companyId, String path, WorkspaceAccessContext actor) {
        WorkspaceEntity entity = workspaceByPath(companyId, path);
        permissionService.assertGranted(entity.getWorkspaceId(), actor, WorkspacePermissionActions.READ);
        return entity.toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkspaceRef> list(WorkspaceListQuery query, Pageable pageable, WorkspaceAccessContext actor) {
        WorkspaceAccessContext resolved = requireActor(actor);
        if (!resolved.platformAdmin()) {
            throw new AccessDeniedException("Workspace management permission required");
        }
        return workspaceRepository.findAll(listSpecification(query), safeListPageable(pageable))
                .map(WorkspaceEntity::toRef);
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
        archive(workspaceId, actor, false);
    }

    @Override
    @Transactional
    public WorkspaceRef archive(Long workspaceId, WorkspaceAccessContext actor, boolean cascade) {
        WorkspaceAccessContext resolved = requireActor(actor);
        permissionService.assertGranted(workspaceId, resolved, WorkspacePermissionActions.ARCHIVE);
        WorkspaceEntity entity = workspace(workspaceId);
        List<WorkspaceEntity> descendants = descendantsOf(entity);
        List<WorkspaceEntity> activeDescendants = descendants.stream()
                .filter(descendant -> !descendant.isArchived())
                .toList();
        if (!cascade && !activeDescendants.isEmpty()) {
            throw new WorkspaceConflictException("Workspace has active descendants; cascade archive is required");
        }
        if (cascade) {
            assertGranted(activeDescendants, resolved, WorkspacePermissionActions.ARCHIVE);
        }
        Instant now = Instant.now();
        Long userId = resolved.requireUserId();
        archiveEntity(entity, now, userId);
        if (cascade) {
            activeDescendants.forEach(descendant -> archiveEntity(descendant, now, userId));
            workspaceRepository.saveAll(activeDescendants);
        }
        return workspaceRepository.save(entity).toRef();
    }

    @Override
    @Transactional
    public WorkspaceRef activate(Long workspaceId, WorkspaceAccessContext actor, boolean cascade) {
        WorkspaceAccessContext resolved = requireActor(actor);
        permissionService.assertGranted(workspaceId, resolved, WorkspacePermissionActions.ACTIVATE);
        WorkspaceEntity entity = workspace(workspaceId);
        rejectArchivedAncestorActivation(entity);
        Long userId = resolved.requireUserId();
        activateEntity(entity, userId);
        if (cascade) {
            List<WorkspaceEntity> archivedDescendants = descendantsOf(entity).stream()
                    .filter(WorkspaceEntity::isArchived)
                    .toList();
            assertGranted(archivedDescendants, resolved, WorkspacePermissionActions.ACTIVATE);
            archivedDescendants.forEach(descendant -> activateEntity(descendant, userId));
            workspaceRepository.saveAll(archivedDescendants);
        }
        return workspaceRepository.save(entity).toRef();
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

    private boolean sameParent(Long currentParentId, Long newParentId) {
        return currentParentId == null ? newParentId == null : currentParentId.equals(newParentId);
    }

    private boolean sameCompany(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private void rejectArchivedMutation(WorkspaceEntity entity) {
        if (entity.isArchived()) {
            throw new WorkspaceValidationException("Archived workspace cannot be mutated");
        }
    }

    private void rejectArchivedAncestorActivation(WorkspaceEntity entity) {
        List<Long> ancestorIds = closureRepository.findAncestorIds(entity.getWorkspaceId());
        if (ancestorIds.isEmpty()) {
            return;
        }
        for (WorkspaceEntity ancestor : workspaceRepository.findByWorkspaceIdIn(ancestorIds)) {
            if (!ancestor.getWorkspaceId().equals(entity.getWorkspaceId()) && ancestor.isArchived()) {
                throw new WorkspaceConflictException("Workspace cannot be activated while an ancestor is archived");
            }
        }
    }

    private void assertGranted(List<WorkspaceEntity> entities, WorkspaceAccessContext actor, String action) {
        for (WorkspaceEntity entity : entities) {
            permissionService.assertGranted(entity.getWorkspaceId(), actor, action);
        }
    }

    private void archiveEntity(WorkspaceEntity entity, Instant archivedAt, Long userId) {
        if (entity.isArchived()) {
            return;
        }
        entity.setArchived(true);
        entity.setArchivedAt(archivedAt);
        entity.setArchivedBy(userId);
        entity.setUpdatedBy(userId);
    }

    private void activateEntity(WorkspaceEntity entity, Long userId) {
        if (!entity.isArchived()) {
            return;
        }
        entity.setArchived(false);
        entity.setArchivedAt(null);
        entity.setArchivedBy(null);
        entity.setUpdatedBy(userId);
    }

    private List<WorkspaceEntity> descendantsOf(WorkspaceEntity entity) {
        List<Long> descendantIds = closureRepository.findDescendantIds(entity.getWorkspaceId()).stream()
                .filter(id -> !id.equals(entity.getWorkspaceId()))
                .toList();
        if (descendantIds.isEmpty()) {
            return List.of();
        }
        return workspaceRepository.findByWorkspaceIdIn(descendantIds);
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

    private WorkspaceEntity workspaceByPath(Long companyId, String path) {
        if (!StringUtils.hasText(path)) {
            throw new WorkspaceValidationException("Workspace path is required");
        }
        Long normalizedCompanyId = normalizeCompanyId(companyId);
        if (requiresCompanyId() && normalizedCompanyId == null) {
            throw new WorkspaceValidationException("Workspace companyId is required");
        }
        if (normalizedCompanyId == null) {
            return workspaceByPath(path);
        }
        return workspaceRepository.findByCompanyIdAndPath(normalizedCompanyId, path.trim())
                .orElseThrow(() -> new WorkspaceNotFoundException(
                        "Workspace not found by company/path: " + normalizedCompanyId + "/" + path));
    }

    private Long normalizeCompanyId(Long companyId) {
        if (companyId == null) {
            return null;
        }
        if (companyId <= 0) {
            throw new WorkspaceValidationException("Workspace companyId must be positive");
        }
        return companyId;
    }

    private void validateCompanyExists(Long companyId) {
        if (companyId == null) {
            return;
        }
        if (companyService != null) {
            companyService.get(companyId);
            return;
        }
        if (settings.companyRequired() || settings.companyScopeEnforced()) {
            throw new IllegalStateException("ApplicationCompanyService is required to validate workspace companyId");
        }
    }

    private boolean existsByScopedPath(Long companyId, String path) {
        return companyId == null
                ? workspaceRepository.existsByPath(path)
                : workspaceRepository.existsByCompanyIdAndPath(companyId, path);
    }

    private boolean existsByScopedRootSlug(Long companyId, String slug) {
        if (settings.companyScopeEnforced() && companyId != null) {
            return workspaceRepository.existsByCompanyIdAndParentIdIsNullAndSlug(companyId, slug);
        }
        return workspaceRepository.existsByParentIdIsNullAndSlug(slug);
    }

    private boolean requiresCompanyId() {
        return settings.companyRequired() || settings.companyScopeEnforced();
    }

    private Specification<WorkspaceEntity> listSpecification(WorkspaceListQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                if (StringUtils.hasText(query.q())) {
                    String pattern = "%" + escapeLike(query.q().trim().toLowerCase(Locale.ROOT)) + "%";
                    predicates.add(builder.or(
                            builder.like(builder.lower(root.get("name")), pattern, '\\'),
                            builder.like(builder.lower(root.get("slug")), pattern, '\\'),
                            builder.like(builder.lower(root.get("path")), pattern, '\\')));
                }
                if (query.rootOnlyEnabled()) {
                    predicates.add(builder.isNull(root.get("parentId")));
                } else if (query.parentId() != null) {
                    predicates.add(builder.equal(root.get("parentId"), query.parentId()));
                }
                if (query.archived() != null) {
                    predicates.add(builder.equal(root.get("archived"), query.archived()));
                }
                if (query.companyId() != null) {
                    predicates.add(builder.equal(root.get("companyId"), normalizeCompanyId(query.companyId())));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Pageable safeListPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, DEFAULT_LIST_SORT);
        }
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 200);
        return PageRequest.of(page, size, safeListSort(pageable.getSort()));
    }

    private Sort safeListSort(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return DEFAULT_LIST_SORT;
        }
        List<Sort.Order> orders = requested.stream()
                .map(order -> {
                    String property = LIST_SORT_PROPERTIES.get(order.getProperty());
                    if (property == null) {
                        return null;
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .filter(order -> order != null)
                .toList();
        return orders.isEmpty() ? DEFAULT_LIST_SORT : Sort.by(orders);
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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

    private void collectSubtree(
            WorkspaceEntity entity,
            int relativeDepth,
            List<WorkspaceEntity> subtree,
            Map<Long, Integer> relativeDepths,
            Set<Long> visited) {
        if (!visited.add(entity.getWorkspaceId())) {
            throw new WorkspaceConflictException("Workspace tree contains a cycle");
        }
        subtree.add(entity);
        relativeDepths.put(entity.getWorkspaceId(), relativeDepth);
        for (WorkspaceEntity child : workspaceRepository.findByParentIdOrderByPositionAscWorkspaceIdAsc(
                entity.getWorkspaceId())) {
            collectSubtree(child, relativeDepth + 1, subtree, relativeDepths, visited);
        }
    }

    private List<WorkspaceAncestor> collectAncestorDistances(WorkspaceEntity entity) {
        List<WorkspaceAncestor> ancestors = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        WorkspaceEntity current = entity;
        int depth = 0;
        while (current != null) {
            if (!visited.add(current.getWorkspaceId())) {
                throw new WorkspaceConflictException("Workspace tree contains a cycle");
            }
            ancestors.add(new WorkspaceAncestor(current.getWorkspaceId(), depth));
            Long parentId = current.getParentId();
            current = parentId == null ? null : workspace(parentId);
            depth++;
        }
        return ancestors;
    }

    private List<WorkspaceClosureEntity> rebuildClosureRows(
            List<WorkspaceEntity> subtree,
            Map<Long, Integer> relativeDepths,
            List<WorkspaceAncestor> newParentAncestors) {
        Map<Long, WorkspaceEntity> subtreeById = new HashMap<>();
        for (WorkspaceEntity descendant : subtree) {
            subtreeById.put(descendant.getWorkspaceId(), descendant);
        }

        List<WorkspaceClosureEntity> closures = new ArrayList<>();
        for (WorkspaceEntity descendant : subtree) {
            Long descendantId = descendant.getWorkspaceId();
            int descendantRelativeDepth = relativeDepths.get(descendantId);
            closures.add(new WorkspaceClosureEntity(descendantId, descendantId, 0));

            Long ancestorId = descendant.getParentId();
            while (ancestorId != null && subtreeById.containsKey(ancestorId)) {
                int ancestorRelativeDepth = relativeDepths.get(ancestorId);
                closures.add(new WorkspaceClosureEntity(
                        ancestorId,
                        descendantId,
                        descendantRelativeDepth - ancestorRelativeDepth));
                ancestorId = subtreeById.get(ancestorId).getParentId();
            }

            for (WorkspaceAncestor ancestor : newParentAncestors) {
                closures.add(new WorkspaceClosureEntity(
                        ancestor.workspaceId(),
                        descendantId,
                        ancestor.depthToNewParent() + descendantRelativeDepth + 1));
            }
        }
        return closures;
    }

    private List<WorkspaceClosureEntity> rebuildAncestorClosureRows(List<WorkspaceAncestor> ancestors) {
        List<WorkspaceClosureEntity> closures = new ArrayList<>();
        for (WorkspaceAncestor descendant : ancestors) {
            for (WorkspaceAncestor ancestor : ancestors) {
                if (ancestor.depthToNewParent() >= descendant.depthToNewParent()) {
                    closures.add(new WorkspaceClosureEntity(
                            ancestor.workspaceId(),
                            descendant.workspaceId(),
                            ancestor.depthToNewParent() - descendant.depthToNewParent()));
                }
            }
        }
        return closures;
    }

    private record WorkspaceAncestor(Long workspaceId, int depthToNewParent) {
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
