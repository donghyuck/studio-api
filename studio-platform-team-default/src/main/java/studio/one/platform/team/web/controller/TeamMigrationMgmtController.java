package studio.one.platform.team.web.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.command.DryRunTeamMigrationCommand;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.domain.model.TeamMigrationRef;
import studio.one.platform.team.web.dto.request.TeamMigrationApplyRequest;
import studio.one.platform.team.web.dto.request.TeamMigrationDryRunRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.team.web.mgmt-migration-base-path:/api/mgmt/team-migrations}")
public class TeamMigrationMgmtController {
    private final TeamMigrationService migrationService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    public TeamMigrationMgmtController(
            TeamMigrationService migrationService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        this.migrationService = migrationService;
        this.principalResolverProvider = principalResolverProvider;
    }

    @PostMapping("/dry-run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamMigrationRef>> dryRun(
            @Valid @RequestBody TeamMigrationDryRunRequest request) {
        TeamMigrationRef run = migrationService.dryRun(new DryRunTeamMigrationCommand(
                request.idempotencyKey(),
                request.targetTeamId(),
                request.sourceRootWorkspaceIds(),
                context()));
        return ResponseEntity.ok(ApiResponse.ok(run));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamMigrationRef>> apply(
            @Valid @RequestBody TeamMigrationApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(migrationService.apply(request.runId(), context())));
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamMigrationRef>> get(@PathVariable String runId) {
        return ResponseEntity.ok(ApiResponse.ok(migrationService.get(runId, context())));
    }

    @PostMapping("/{runId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamMigrationRef>> verify(@PathVariable String runId) {
        return ResponseEntity.ok(ApiResponse.ok(migrationService.verify(runId, context())));
    }

    @PostMapping("/{runId}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamMigrationRef>> rollback(@PathVariable String runId) {
        return ResponseEntity.ok(ApiResponse.ok(migrationService.rollback(runId, context())));
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
