package studio.one.application.template.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.application.template.domain.model.Template;
import studio.one.application.template.service.TemplatesService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;

@ExtendWith(MockitoExtension.class)
class TemplateControllerAuthorizationTest {

    @Mock
    private TemplatesService templatesService;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private PrincipalResolver principalResolver;

    @Test
    void listUsesCreatorScopedQueryForNonAdmin() {
        TemplateController controller = new TemplateController(templatesService, principalResolverProvider);
        ApplicationPrincipal principal = principal(7L, "ROLE_USER");

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(templatesService.pageByCreatedBy(7L, Pageable.unpaged(), "abc", "name"))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        controller.list(Pageable.unpaged(), "abc", "name");

        verify(templatesService).pageByCreatedBy(7L, Pageable.unpaged(), "abc", "name");
    }

    @Test
    void getRejectsOtherUsersTemplateForNonAdmin() throws Exception {
        TemplateController controller = new TemplateController(templatesService, principalResolverProvider);
        ApplicationPrincipal principal = principal(7L, "USER");

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(templatesService.getTemplates(10L, 7L))
                .thenReturn(mock(Template.class));

        controller.get(10L);
        verify(templatesService).getTemplates(10L, 7L);
    }

    @Test
    void listUsesGlobalQueryForAdmin() {
        TemplateController controller = new TemplateController(templatesService, principalResolverProvider);
        ApplicationPrincipal principal = principal(1L, "ADMIN");

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(templatesService.page(Pageable.unpaged(), null, null))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        controller.list(Pageable.unpaged(), null, null);

        verify(templatesService).page(Pageable.unpaged(), null, null);
    }

    @Test
    void getFailsWhenPrincipalResolverMissing() {
        TemplateController controller = new TemplateController(templatesService, principalResolverProvider);
        when(principalResolverProvider.getIfAvailable()).thenReturn(null);

        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> controller.get(1L));
    }

    private ApplicationPrincipal principal(Long userId, String role) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return "user-" + userId;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of(role);
            }
        };
    }
}
