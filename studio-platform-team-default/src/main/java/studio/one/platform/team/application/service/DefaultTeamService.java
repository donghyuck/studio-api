package studio.one.platform.team.application.service;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import studio.one.platform.team.application.command.CreateTeamCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.command.TeamListQuery;
import studio.one.platform.team.application.command.UpdateTeamCommand;
import studio.one.platform.team.application.error.TeamConflictException;
import studio.one.platform.team.application.error.TeamNotFoundException;
import studio.one.platform.team.application.error.TeamValidationException;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamCompanyAssignmentPort;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.application.usecase.TeamWorkspaceProvisioningPort;
import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamMemberStatus;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamRef;
import studio.one.platform.team.domain.model.TeamRole;
import studio.one.platform.team.domain.model.TeamStatus;
import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;

public class DefaultTeamService implements TeamService {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final TeamJpaRepository teamRepository;
    private final TeamMemberJpaRepository memberRepository;
    private final TeamAuthorizationPort authorizationPort;
    private final TeamCompanyAssignmentPort companyAssignmentPort;
    private final TeamWorkspaceProvisioningPort workspaceProvisioningPort;

    public DefaultTeamService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamAuthorizationPort authorizationPort,
            TeamCompanyAssignmentPort companyAssignmentPort,
            TeamWorkspaceProvisioningPort workspaceProvisioningPort) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.authorizationPort = authorizationPort;
        this.companyAssignmentPort = companyAssignmentPort;
        this.workspaceProvisioningPort = workspaceProvisioningPort;
    }

    @Override
    @Transactional
    public TeamRef create(CreateTeamCommand command) {
        TeamAccessContext actor = requireActor(command.actor());
        Long companyId = normalizeId(command.companyId());
        assertCompanyAssignment(companyId, actor);
        String slug = normalizeSlug(command.slug());
        if (teamRepository.existsBySlugIgnoreCase(slug)) {
            throw new TeamConflictException("Duplicate team slug: " + slug);
        }
        TeamEntity team = new TeamEntity();
        team.setCompanyId(companyId);
        team.setName(normalizeName(command.name()));
        team.setSlug(slug);
        team.setDescription(normalizeDescription(command.description()));
        team.setVisibility(command.visibility() == null ? TeamVisibility.PRIVATE : command.visibility());
        team.setJoinPolicy(command.joinPolicy() == null ? TeamJoinPolicy.INVITE_ONLY : command.joinPolicy());
        team.setRagEnabled(command.ragEnabled() == null || command.ragEnabled());
        team.setRagReplyMode(command.ragReplyMode() == null ? TeamRagReplyMode.MENTION : command.ragReplyMode());
        team.setCreatedBy(actor.requireUserId());
        team.setUpdatedBy(actor.requireUserId());
        team = teamRepository.save(team);

        TeamMemberEntity owner = new TeamMemberEntity();
        owner.setTeamId(team.getTeamId());
        owner.setUserId(actor.requireUserId());
        owner.setRole(TeamRole.OWNER);
        owner.setStatus(TeamMemberStatus.ACTIVE);
        owner.setCreatedBy(actor.requireUserId());
        owner.setUpdatedBy(actor.requireUserId());
        memberRepository.save(owner);

        TeamRef created = team.toRef();
        boolean provisionRootWorkspace = command.provisionRootWorkspace() == null
                || command.provisionRootWorkspace();
        if (!provisionRootWorkspace && !actor.platformAdmin()) {
            throw new AccessDeniedException("Only a platform administrator can create a migration target Team");
        }
        if (provisionRootWorkspace && workspaceProvisioningPort == null) {
            throw new IllegalStateException("Team root Workspace provisioning is unavailable");
        }
        if (provisionRootWorkspace) {
            workspaceProvisioningPort.createRootWorkspace(created, actor);
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public TeamRef get(Long teamId, TeamAccessContext actor) {
        TeamEntity team = team(teamId);
        if (!canReadMetadata(team, actor)) {
            throw new AccessDeniedException("Team metadata permission denied");
        }
        return team.toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TeamRef> list(TeamListQuery query, Pageable pageable, TeamAccessContext actor) {
        TeamListQuery resolved = query == null ? TeamListQuery.all() : query;
        Long userId = actor == null ? null : actor.userId();
        Specification<TeamEntity> spec = (root, criteriaQuery, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            if (resolved.status() != null) {
                predicates.add(cb.equal(root.get("status"), resolved.status()));
            }
            if (resolved.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), resolved.companyId()));
            }
            if (resolved.visibility() != null) {
                predicates.add(cb.equal(root.get("visibility"), resolved.visibility()));
            }
            if (StringUtils.hasText(resolved.keyword())) {
                String like = "%" + resolved.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("slug")), like)));
            }
            if (actor == null || !actor.platformAdmin()) {
                var membership = criteriaQuery.subquery(Long.class);
                var member = membership.from(TeamMemberEntity.class);
                membership.select(member.get("teamId"));
                membership.where(
                        cb.equal(member.get("teamId"), root.get("teamId")),
                        cb.equal(member.get("userId"), userId == null ? -1L : userId),
                        cb.equal(member.get("status"), TeamMemberStatus.ACTIVE));
                predicates.add(cb.or(
                        cb.equal(root.get("visibility"), TeamVisibility.PUBLIC),
                        cb.exists(membership)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return teamRepository.findAll(spec, pageable).map(TeamEntity::toRef);
    }

    @Override
    @Transactional
    public TeamRef update(Long teamId, UpdateTeamCommand command) {
        TeamAccessContext actor = requireActor(command.actor());
        assertGranted(teamId, actor, TeamPermissionActions.UPDATE);
        TeamEntity team = teamRepository.findForUpdate(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
        if (team.getStatus() == TeamStatus.ARCHIVED) {
            throw new TeamValidationException("Archived team cannot be updated");
        }
        if (command.companyIdSpecified()) {
            Long companyId = normalizeId(command.companyId());
            assertCompanyAssignment(companyId, actor);
            team.setCompanyId(companyId);
        }
        if (command.name() != null) {
            team.setName(normalizeName(command.name()));
        }
        if (command.description() != null) {
            team.setDescription(normalizeDescription(command.description()));
        }
        if (command.visibility() != null) {
            team.setVisibility(command.visibility());
        }
        if (command.joinPolicy() != null) {
            team.setJoinPolicy(command.joinPolicy());
        }
        if (command.ragEnabled() != null) {
            team.setRagEnabled(command.ragEnabled());
        }
        if (command.ragReplyMode() != null) {
            team.setRagReplyMode(command.ragReplyMode());
        }
        team.setUpdatedBy(actor.requireUserId());
        return teamRepository.save(team).toRef();
    }

    @Override
    @Transactional
    public TeamRef archive(Long teamId, TeamAccessContext actor) {
        TeamAccessContext resolved = requireActor(actor);
        assertGranted(teamId, resolved, TeamPermissionActions.ARCHIVE);
        TeamEntity team = teamRepository.findForUpdate(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
        if (team.getStatus() != TeamStatus.ARCHIVED) {
            team.setStatus(TeamStatus.ARCHIVED);
            team.setArchivedAt(Instant.now());
            team.setArchivedBy(resolved.requireUserId());
            team.setUpdatedBy(resolved.requireUserId());
            team.setPermissionVersion(team.getPermissionVersion() + 1L);
        }
        return teamRepository.save(team).toRef();
    }

    private boolean canReadMetadata(TeamEntity team, TeamAccessContext actor) {
        return actor != null && actor.platformAdmin()
                || team.getVisibility() == TeamVisibility.PUBLIC
                || actor != null && authorizationPort.isMember(team.getTeamId(), actor.userId());
    }

    private void assertGranted(Long teamId, TeamAccessContext actor, String action) {
        if (actor.platformAdmin()) {
            return;
        }
        authorizationPort.assertGranted(teamId, actor.requireUserId(), action);
    }

    private void assertCompanyAssignment(Long companyId, TeamAccessContext actor) {
        if (companyId == null) {
            return;
        }
        if (companyAssignmentPort == null) {
            throw new IllegalStateException("Company-assigned teams require TeamCompanyAssignmentPort");
        }
        companyAssignmentPort.assertCanAssign(companyId, actor);
    }

    private TeamEntity team(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException("Team not found: " + teamId));
    }

    private TeamAccessContext requireActor(TeamAccessContext actor) {
        if (actor == null) {
            throw new TeamValidationException("team actor is required");
        }
        actor.requireUserId();
        return actor;
    }

    private Long normalizeId(Long value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new TeamValidationException("companyId must be positive");
        }
        return value;
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new TeamValidationException("team name is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new TeamValidationException("team name is too long");
        }
        return normalized;
    }

    private String normalizeSlug(String value) {
        if (!StringUtils.hasText(value)) {
            throw new TeamValidationException("team slug is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100 || !SLUG.matcher(normalized).matches()) {
            throw new TeamValidationException("team slug must contain lowercase letters, digits, or hyphens");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new TeamValidationException("team description is too long");
        }
        return normalized;
    }
}
