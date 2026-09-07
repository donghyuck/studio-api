package studio.one.platform.team.web.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.command.CreateTeamCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.command.TeamListQuery;
import studio.one.platform.team.application.command.TeamMemberCommand;
import studio.one.platform.team.application.command.UpdateTeamCommand;
import studio.one.platform.team.application.usecase.TeamMemberService;
import studio.one.platform.team.application.usecase.TeamJoinService;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.domain.model.TeamMemberRef;
import studio.one.platform.team.domain.model.TeamJoinRequestRef;
import studio.one.platform.team.domain.model.TeamJoinRequestStatus;
import studio.one.platform.team.domain.model.TeamJoinResult;
import studio.one.platform.team.domain.model.TeamRef;
import studio.one.platform.team.domain.model.TeamStatus;
import studio.one.platform.team.domain.model.TeamVisibility;
import studio.one.platform.team.web.dto.request.TeamCreateRequest;
import studio.one.platform.team.web.dto.request.TeamMemberRequest;
import studio.one.platform.team.web.dto.request.TeamMemberRoleRequest;
import studio.one.platform.team.web.dto.request.TeamUpdateRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.team.web.public-base-path:/api/teams}")
public class TeamController {
    private final TeamService teamService;
    private final TeamMemberService memberService;
    private final TeamJoinService joinService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    public TeamController(
            TeamService teamService,
            TeamMemberService memberService,
            TeamJoinService joinService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        this.teamService = teamService;
        this.memberService = memberService;
        this.joinService = joinService;
        this.principalResolverProvider = principalResolverProvider;
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamRef>> create(@Valid @RequestBody TeamCreateRequest request) {
        TeamAccessContext actor = context();
        TeamRef team = teamService.create(new CreateTeamCommand(
                request.companyId(), request.name(), request.slug(), request.description(), request.visibility(),
                request.joinPolicy(), request.ragEnabled(), request.ragReplyMode(),
                request.provisionRootWorkspace(), actor));
        return ResponseEntity.ok(ApiResponse.ok(team));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<Page<TeamRef>>> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "companyId", required = false) Long companyId,
            @RequestParam(value = "visibility", required = false) TeamVisibility visibility,
            @RequestParam(value = "status", required = false, defaultValue = "ACTIVE") TeamStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.list(
                new TeamListQuery(keyword, companyId, visibility, status), pageable, context())));
    }

    @GetMapping("/{teamId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<TeamRef>> get(@PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.get(teamId, context())));
    }

    @PatchMapping("/{teamId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamRef>> update(
            @PathVariable Long teamId,
            @Valid @RequestBody TeamUpdateRequest request) {
        TeamRef team = teamService.update(teamId, new UpdateTeamCommand(
                request.companyId(), request.companyIdSpecified(), request.name(), request.description(),
                request.visibility(), request.joinPolicy(), request.ragEnabled(), request.ragReplyMode(), context()));
        return ResponseEntity.ok(ApiResponse.ok(team));
    }

    @PostMapping("/{teamId:[\\p{Digit}]+}/archive")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamRef>> archive(@PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.archive(teamId, context())));
    }

    @GetMapping("/{teamId:[\\p{Digit}]+}/members")
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<List<TeamMemberRef>>> members(@PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMembers(teamId, context())));
    }

    @PostMapping("/{teamId:[\\p{Digit}]+}/members")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamMemberRef>> addMember(
            @PathVariable Long teamId,
            @Valid @RequestBody TeamMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.addMember(
                teamId, new TeamMemberCommand(request.userId(), request.role(), context()))));
    }

    @PatchMapping("/{teamId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamMemberRef>> changeRole(
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @Valid @RequestBody TeamMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.changeRole(
                teamId, new TeamMemberCommand(userId, request.role(), context()))));
    }

    @DeleteMapping("/{teamId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        memberService.removeMember(teamId, userId, context());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{teamId:[\\p{Digit}]+}/join")
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<TeamJoinResult>> join(@PathVariable Long teamId) {
        return ResponseEntity.ok(ApiResponse.ok(joinService.join(teamId, context())));
    }

    @GetMapping("/{teamId:[\\p{Digit}]+}/join-requests")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<List<TeamJoinRequestRef>>> joinRequests(
            @PathVariable Long teamId,
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") TeamJoinRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(joinService.getRequests(teamId, status, context())));
    }

    @PostMapping("/{teamId:[\\p{Digit}]+}/join-requests/{requestId:[\\p{Digit}]+}/approve")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamJoinRequestRef>> approveJoinRequest(
            @PathVariable Long teamId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok(joinService.approve(teamId, requestId, context())));
    }

    @PostMapping("/{teamId:[\\p{Digit}]+}/join-requests/{requestId:[\\p{Digit}]+}/reject")
    @PreAuthorize("@endpointAuthz.can('features:team','write')")
    public ResponseEntity<ApiResponse<TeamJoinRequestRef>> rejectJoinRequest(
            @PathVariable Long teamId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok(joinService.reject(teamId, requestId, context())));
    }

    private TeamAccessContext context() {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        ApplicationPrincipal principal = resolver == null ? null : resolver.currentOrNull();
        if (principal == null || principal.getUserId() == null || principal.getUserId() <= 0) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return new TeamAccessContext(
                principal.getUserId(), principal.getUsername(), isPlatformAdmin(principal));
    }

    private boolean isPlatformAdmin(ApplicationPrincipal principal) {
        return principal.hasRole("ROLE_ADMIN") || principal.hasRole("ADMIN");
    }
}
