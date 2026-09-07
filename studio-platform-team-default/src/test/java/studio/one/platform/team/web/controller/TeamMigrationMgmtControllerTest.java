package studio.one.platform.team.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.web.dto.request.TeamMigrationApplyRequest;

class TeamMigrationMgmtControllerTest {

    @Test
    void everyMigrationEndpointRetainsPlatformAdminAuthorization() {
        Set<String> endpointMethods = Set.of("dryRun", "apply", "get", "verify", "rollback");
        Set<String> checked = new HashSet<>();
        for (Method method : TeamMigrationMgmtController.class.getDeclaredMethods()) {
            if (!endpointMethods.contains(method.getName())) {
                continue;
            }
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization).as(method.getName()).isNotNull();
            assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
            checked.add(method.getName());
        }
        assertThat(checked).containsExactlyInAnyOrderElementsOf(endpointMethods);
    }

    @Test
    void roleAdminPrincipalIsForwardedAsPlatformAdminContext() {
        TeamMigrationService service = org.mockito.Mockito.mock(TeamMigrationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PrincipalResolver> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        PrincipalResolver resolver = org.mockito.Mockito.mock(PrincipalResolver.class);
        ApplicationPrincipal principal = principal(Set.of("ROLE_ADMIN"));
        when(provider.getIfAvailable()).thenReturn(resolver);
        when(resolver.currentOrNull()).thenReturn(principal);
        TeamMigrationMgmtController controller = new TeamMigrationMgmtController(service, provider);

        controller.apply(new TeamMigrationApplyRequest("00000000-0000-0000-0000-000000000001"));

        ArgumentCaptor<TeamAccessContext> actor = ArgumentCaptor.forClass(TeamAccessContext.class);
        verify(service).apply(org.mockito.ArgumentMatchers.anyString(), actor.capture());
        assertThat(actor.getValue().platformAdmin()).isTrue();
    }

    @Test
    void ordinaryPrincipalIsNotElevatedWhenControllerIsCalledDirectly() {
        TeamMigrationService service = org.mockito.Mockito.mock(TeamMigrationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PrincipalResolver> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        PrincipalResolver resolver = org.mockito.Mockito.mock(PrincipalResolver.class);
        when(provider.getIfAvailable()).thenReturn(resolver);
        when(resolver.currentOrNull()).thenReturn(principal(Set.of("ROLE_USER")));
        TeamMigrationMgmtController controller = new TeamMigrationMgmtController(service, provider);

        controller.apply(new TeamMigrationApplyRequest("00000000-0000-0000-0000-000000000001"));

        ArgumentCaptor<TeamAccessContext> actor = ArgumentCaptor.forClass(TeamAccessContext.class);
        verify(service).apply(org.mockito.ArgumentMatchers.anyString(), actor.capture());
        assertThat(actor.getValue().platformAdmin()).isFalse();
    }

    private ApplicationPrincipal principal(Set<String> roles) {
        return new ApplicationPrincipal() {
            @Override public Long getUserId() { return 1L; }
            @Override public String getUsername() { return "admin"; }
            @Override public Set<String> getRoles() { return roles; }
        };
    }
}
