package studio.one.application.template.web.controller;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.application.template.domain.model.DefaultTemplate;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.service.TemplatesService;
import studio.one.application.template.web.dto.TemplateDto;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.identity.UserRef;

@ExtendWith(MockitoExtension.class)
class TemplateMgmtControllerAuthorizationTest {

    @Mock
    private TemplatesService templatesService;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private PrincipalResolver principalResolver;

    @Mock
    private IdentityService identityService;

    @Test
    void listUsesCreatorScopedQueryForNonAdmin() {
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
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
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
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
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
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
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
        when(principalResolverProvider.getIfAvailable()).thenReturn(null);

        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> controller.get(1L));
    }

    @Test
    void getReturnsResolvedUsersWhenIdentityServiceAvailable() throws Exception {
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
        ApplicationPrincipal principal = principal(7L, "ADMIN");
        Template template = template(11L, 101L, 202L);

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(identityServiceProvider.getIfAvailable()).thenReturn(identityService);
        when(identityService.findById(101L)).thenReturn(Optional.of(new UserRef(101L, "creator", Set.of())));
        when(identityService.findById(202L)).thenReturn(Optional.of(new UserRef(202L, "editor", Set.of())));
        when(templatesService.getTemplates(11L)).thenReturn(template);

        TemplateDto body = controller.get(11L).getBody().getData();

        assertEquals(101L, body.getCreatedBy().userId());
        assertEquals("creator", body.getCreatedBy().username());
        assertEquals(202L, body.getUpdatedBy().userId());
        assertEquals("editor", body.getUpdatedBy().username());
    }

    @Test
    void getReturnsNullUsersWhenIdentityServiceMissing() throws Exception {
        TemplateMgmtController controller =
                new TemplateMgmtController(templatesService, identityServiceProvider, principalResolverProvider);
        ApplicationPrincipal principal = principal(7L, "ADMIN");
        Template template = template(11L, 101L, 202L);

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(identityServiceProvider.getIfAvailable()).thenReturn(null);
        when(templatesService.getTemplates(11L)).thenReturn(template);

        TemplateDto body = controller.get(11L).getBody().getData();

        assertNull(body.getCreatedBy());
        assertNull(body.getUpdatedBy());
    }

    private Template template(long templateId, long createdBy, long updatedBy) {
        DefaultTemplate template = new DefaultTemplate();
        template.setTemplateId(templateId);
        template.setObjectType(1);
        template.setObjectId(99L);
        template.setName("welcome");
        template.setDisplayName("Welcome");
        template.setDescription("desc");
        template.setSubject("subject");
        template.setBody("body");
        template.setCreatedBy(createdBy);
        template.setUpdatedBy(updatedBy);
        template.setCreatedAt(Instant.parse("2026-04-14T00:00:00Z"));
        template.setUpdatedAt(Instant.parse("2026-04-14T00:00:00Z"));
        return template;
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
