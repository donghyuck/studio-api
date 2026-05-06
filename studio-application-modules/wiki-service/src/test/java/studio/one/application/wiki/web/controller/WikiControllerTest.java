package studio.one.application.wiki.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMapping;

import studio.one.application.wiki.model.WikiPage;
import studio.one.application.wiki.service.WikiPageArchiveCommand;
import studio.one.application.wiki.service.WikiPageService;
import studio.one.application.wiki.service.WikiPageWriteCommand;
import studio.one.application.wiki.web.dto.WikiArchiveRequest;
import studio.one.application.wiki.web.dto.WikiPageWriteRequest;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.service.WorkspaceAccessContext;

class WikiControllerTest {

    @Test
    void controllersUseSeparatedBasePathProperties() {
        assertThat(WikiController.class.getAnnotation(RequestMapping.class).value())
                .contains("${studio.features.wiki.web.public-base-path:/api/workspaces}/{workspaceId:[\\p{Digit}]+}/wiki");
        assertThat(WikiMgmtController.class.getAnnotation(RequestMapping.class).value())
                .contains("${studio.features.wiki.web.mgmt-base-path:/api/mgmt/workspaces}/{workspaceId:[\\p{Digit}]+}/wiki");
    }

    @Test
    void userControllerUsesNonAdminAccessContext() {
        WikiPageService wikiPageService = org.mockito.Mockito.mock(WikiPageService.class);
        when(wikiPageService.putPage(eq(1L), eq("Home"), any())).thenReturn(page());
        WikiController controller = new WikiController(wikiPageService, principalProvider("admin", true));

        controller.putPage(1L, "Home", new WikiPageWriteRequest("Home", "markdown", null));

        ArgumentCaptor<WikiPageWriteCommand> captor = ArgumentCaptor.forClass(WikiPageWriteCommand.class);
        verify(wikiPageService).putPage(eq(1L), eq("Home"), captor.capture());
        WorkspaceAccessContext actor = captor.getValue().actor();
        assertThat(actor.userId()).isEqualTo(10L);
        assertThat(actor.platformAdmin()).isFalse();
    }

    @Test
    void mgmtControllerUsesPlatformAdminAccessContext() {
        WikiPageService wikiPageService = org.mockito.Mockito.mock(WikiPageService.class);
        when(wikiPageService.putPage(eq(1L), eq("Home"), any())).thenReturn(page());
        WikiMgmtController controller = new WikiMgmtController(wikiPageService, principalProvider("admin", false));

        controller.putPage(1L, "Home", new WikiPageWriteRequest("Home", "markdown", null));

        ArgumentCaptor<WikiPageWriteCommand> captor = ArgumentCaptor.forClass(WikiPageWriteCommand.class);
        verify(wikiPageService).putPage(eq(1L), eq("Home"), captor.capture());
        assertThat(captor.getValue().actor().platformAdmin()).isTrue();
    }

    @Test
    void archiveRequestPassesBaseRevisionIdToService() {
        WikiPageService wikiPageService = org.mockito.Mockito.mock(WikiPageService.class);
        WikiController controller = new WikiController(wikiPageService, principalProvider("user", false));

        controller.archivePage(1L, "Home", new WikiArchiveRequest(7L));

        ArgumentCaptor<WikiPageArchiveCommand> captor = ArgumentCaptor.forClass(WikiPageArchiveCommand.class);
        verify(wikiPageService).archivePage(eq(1L), eq("Home"), captor.capture());
        assertThat(captor.getValue().baseRevisionId()).isEqualTo(7L);
        assertThat(captor.getValue().actor().platformAdmin()).isFalse();
    }

    private WikiPage page() {
        Instant now = Instant.now();
        return new WikiPage(1L, 1L, "Home", "Home", "markdown", "<p>markdown</p>", 1L, 1, false, now, now);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PrincipalResolver> principalProvider(String username, boolean adminRole) {
        ObjectProvider<PrincipalResolver> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        PrincipalResolver resolver = () -> new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return 10L;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Set<String> getRoles() {
                return adminRole ? Set.of("ADMIN") : Set.of();
            }
        };
        when(provider.getIfAvailable()).thenReturn(resolver);
        return provider;
    }
}
