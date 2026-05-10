package studio.one.platform.workspace.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.workspace.application.error.WorkspaceConflictException;
import studio.one.platform.workspace.application.error.WorkspaceNotFoundException;
import studio.one.platform.workspace.application.error.WorkspaceValidationException;
import studio.one.platform.workspace.domain.model.WorkspaceMemberRef;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.command.WorkspaceMemberCommand;
import studio.one.platform.workspace.application.command.WorkspaceMemberListQuery;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

@RequiredArgsConstructor
public class DefaultWorkspaceMemberService implements WorkspaceMemberService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Sort DEFAULT_MEMBER_SORT = Sort.by("userId").ascending();
    private static final Map<String, String> JPA_SORT_PROPERTIES = Map.ofEntries(
            Map.entry("id", "userId"),
            Map.entry("userId", "userId"),
            Map.entry("workspaceId", "workspaceId"),
            Map.entry("role", "role"));
    private static final Map<String, String> REF_SORT_PROPERTIES = Map.ofEntries(
            Map.entry("id", "userId"),
            Map.entry("userId", "userId"),
            Map.entry("workspaceId", "workspaceId"),
            Map.entry("role", "role"),
            Map.entry("inherited", "inherited"));

    private final WorkspaceJpaRepository workspaceRepository;
    private final WorkspaceClosureJpaRepository closureRepository;
    private final WorkspaceMemberJpaRepository memberRepository;
    private final WorkspacePermissionService permissionService;
    private final EntityManager entityManager;
    private final ApplicationUserService<? extends User, ? extends Role> userService;

    public DefaultWorkspaceMemberService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            EntityManager entityManager) {
        this(workspaceRepository, closureRepository, memberRepository, permissionService, entityManager, null);
    }

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
    public Page<WorkspaceMemberRef> getDirectMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor) {
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_READ);
        WorkspaceMemberListQuery resolved = queryOrAll(query);
        Pageable safePageable = safeMemberPageable(pageable, false);
        if (Boolean.TRUE.equals(resolved.inherited())) {
            return Page.empty(safePageable);
        }
        Set<Long> matchedUserIds = matchedUserIds(resolved, List.of(workspaceId));
        if (resolved.hasKeyword() && matchedUserIds.isEmpty()) {
            return Page.empty(safePageable);
        }
        return memberRepository.findAll(
                        directMemberSpecification(workspaceId, resolved, matchedUserIds),
                        safePageable)
                .map(member -> member.toRef(false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMemberRef> getEffectiveMembers(Long workspaceId, WorkspaceAccessContext actor) {
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_READ);
        return effectiveMemberList(workspaceId, WorkspaceMemberListQuery.all(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkspaceMemberRef> getEffectiveMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor) {
        requireWorkspace(workspaceId);
        permissionService.assertGranted(workspaceId, actor, WorkspacePermissionActions.MEMBER_READ);
        WorkspaceMemberListQuery resolved = queryOrAll(query);
        Pageable safePageable = safeMemberPageable(pageable, true);
        List<Long> ancestorIds = closureRepository.findAncestorIds(workspaceId);
        Set<Long> matchedUserIds = matchedUserIds(resolved, ancestorIds);
        if (resolved.hasKeyword() && matchedUserIds.isEmpty()) {
            return Page.empty(safePageable);
        }
        List<WorkspaceMemberRef> filtered = effectiveMemberList(workspaceId, ancestorIds, resolved, matchedUserIds).stream()
                .sorted(memberRefComparator(safePageable.getSort()))
                .toList();
        return page(filtered, safePageable);
    }

    private List<WorkspaceMemberRef> effectiveMemberList(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Set<Long> matchedUserIds) {
        return effectiveMemberList(workspaceId, closureRepository.findAncestorIds(workspaceId), query, matchedUserIds);
    }

    private List<WorkspaceMemberRef> effectiveMemberList(
            Long workspaceId,
            List<Long> ancestorIds,
            WorkspaceMemberListQuery query,
            Set<Long> matchedUserIds) {
        Map<Long, WorkspaceMemberRef> strongest = new LinkedHashMap<>();
        for (WorkspaceMemberEntity member : memberRepository.findByWorkspaceIdIn(ancestorIds)) {
            if (matchedUserIds != null && !matchedUserIds.contains(member.getUserId())) {
                continue;
            }
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
                .filter(member -> query.role() == null || member.role() == query.role())
                .filter(member -> query.inherited() == null || member.inherited() == query.inherited())
                .sorted((left, right) -> Long.compare(left.userId(), right.userId()))
                .toList();
    }

    private Specification<WorkspaceMemberEntity> directMemberSpecification(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Set<Long> matchedUserIds) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("workspaceId"), workspaceId));
            if (query.role() != null) {
                predicates.add(builder.equal(root.get("role"), query.role()));
            }
            if (matchedUserIds != null) {
                predicates.add(root.get("userId").in(matchedUserIds));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Set<Long> matchedUserIds(WorkspaceMemberListQuery query, Collection<Long> workspaceIds) {
        String keyword = query.normalizedKeyword();
        if (keyword == null) {
            return null;
        }
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        try {
            result.add(Long.parseLong(keyword));
        } catch (NumberFormatException ignored) {
            // Numeric keyword support is additive; non-numeric keywords use user metadata search.
        }
        if (userService != null) {
            Set<Long> workspaceUserIds = workspaceUserIds(workspaceIds);
            searchUsers(keyword, workspaceUserIds).stream()
                    .map(User::getUserId)
                    .forEach(result::add);
        } else {
            searchUsersViaDatabase(keyword, workspaceIds).forEach(result::add);
        }
        return result;
    }

    private List<? extends User> searchUsers(String keyword, Set<Long> workspaceUserIds) {
        if (workspaceUserIds.isEmpty()) {
            return List.of();
        }
        List<User> matches = new ArrayList<>();
        int page = 0;
        Page<? extends User> users;
        do {
            users = userService.findByNameOrUsernameOrEmail(keyword, PageRequest.of(page++, MAX_PAGE_SIZE));
            users.getContent().stream()
                    .filter(user -> workspaceUserIds.contains(user.getUserId()))
                    .forEach(matches::add);
        } while (users.hasNext() && matches.size() < workspaceUserIds.size());
        return matches;
    }

    private Set<Long> workspaceUserIds(Collection<Long> workspaceIds) {
        return memberRepository.findByWorkspaceIdIn(workspaceIds).stream()
                .map(WorkspaceMemberEntity::getUserId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<Long> searchUsersViaDatabase(String keyword, Collection<Long> workspaceIds) {
        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                        select distinct m.USER_ID
                          from TB_PLATFORM_WORKSPACE_MEMBER m
                          left join TB_APPLICATION_USER u on u.USER_ID = m.USER_ID
                         where m.WORKSPACE_ID in (:workspaceIds)
                           and (lower(coalesce(u.USERNAME, '')) like :pattern escape '!'
                            or lower(coalesce(u.NAME, '')) like :pattern escape '!'
                            or lower(coalesce(u.EMAIL, '')) like :pattern escape '!')
                        """)
                .setParameter("workspaceIds", workspaceIds)
                .setParameter("pattern", pattern)
                .getResultList();
        return rows.stream()
                .map(Number::longValue)
                .toList();
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private WorkspaceMemberListQuery queryOrAll(WorkspaceMemberListQuery query) {
        return query == null ? WorkspaceMemberListQuery.all() : query;
    }

    private Pageable safeMemberPageable(Pageable pageable, boolean includeInheritedSort) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, DEFAULT_MEMBER_SORT);
        }
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Sort sort = includeInheritedSort
                ? safeSort(pageable.getSort(), REF_SORT_PROPERTIES)
                : safeSort(pageable.getSort(), JPA_SORT_PROPERTIES);
        return PageRequest.of(page, size, sort);
    }

    private Sort safeSort(Sort requested, Map<String, String> properties) {
        if (requested == null || requested.isUnsorted()) {
            return DEFAULT_MEMBER_SORT;
        }
        List<Sort.Order> orders = requested.stream()
                .map(order -> {
                    String property = properties.get(order.getProperty());
                    if (property == null) {
                        return null;
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .filter(order -> order != null)
                .toList();
        return orders.isEmpty() ? DEFAULT_MEMBER_SORT : Sort.by(orders);
    }

    private Comparator<WorkspaceMemberRef> memberRefComparator(Sort sort) {
        Comparator<WorkspaceMemberRef> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<WorkspaceMemberRef> next = switch (order.getProperty()) {
                case "workspaceId" -> Comparator.comparing(WorkspaceMemberRef::workspaceId);
                case "role" -> Comparator.comparing(member -> member.role().rank());
                case "inherited" -> Comparator.comparing(WorkspaceMemberRef::inherited);
                case "userId" -> Comparator.comparing(WorkspaceMemberRef::userId);
                default -> Comparator.comparing(WorkspaceMemberRef::userId);
            };
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator == null ? Comparator.comparing(WorkspaceMemberRef::userId) : comparator;
    }

    private Page<WorkspaceMemberRef> page(List<WorkspaceMemberRef> members, Pageable pageable) {
        int start = Math.toIntExact(Math.min(pageable.getOffset(), members.size()));
        int end = Math.min(start + pageable.getPageSize(), members.size());
        return new PageImpl<>(members.subList(start, end), pageable, members.size());
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
