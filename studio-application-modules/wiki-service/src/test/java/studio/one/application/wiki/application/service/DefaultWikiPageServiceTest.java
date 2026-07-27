package studio.one.application.wiki.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import studio.one.application.wiki.application.error.WikiConflictException;
import studio.one.application.wiki.application.error.WikiNotFoundException;
import studio.one.application.wiki.domain.model.WikiPermissionActions;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageEntity;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageJpaRepository;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageRevisionJpaRepository;
import studio.one.application.wiki.application.command.WikiPageArchiveCommand;
import studio.one.application.wiki.application.command.WikiPageRevertCommand;
import studio.one.application.wiki.application.command.WikiPageWriteCommand;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

@DataJpaTest
@ContextConfiguration(classes = DefaultWikiPageServiceTest.TestApplication.class)
class DefaultWikiPageServiceTest {

    private static final Long WORKSPACE_ID = 100L;
    private static final WorkspaceAccessContext OWNER = new WorkspaceAccessContext(1L, "owner", false);

    @Autowired
    private WikiPageJpaRepository pageRepository;

    @Autowired
    private WikiPageRevisionJpaRepository revisionRepository;

    private WorkspacePermissionService permissionService;
    private DefaultWikiPageService service;

    @BeforeEach
    void setUp() {
        permissionService = mock(WorkspacePermissionService.class);
        service = new DefaultWikiPageService(
                pageRepository,
                revisionRepository,
                permissionService,
                new DefaultWikiRenderService());
    }

    @Test
    void createsUpdatesAndRevertsWikiPageRevisions() {
        var created = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "# Hello", null, OWNER));

        var updated = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "## Updated", created.currentRevisionId(), OWNER));

        var history = service.listRevisions(WORKSPACE_ID, "Home", PageRequest.of(0, 10), OWNER);
        var firstRevisionId = history.getContent().stream()
                .filter(revision -> revision.revisionNo() == 1)
                .findFirst()
                .orElseThrow()
                .revisionId();

        var reverted = service.revertRevision(
                WORKSPACE_ID,
                "Home",
                firstRevisionId,
                new WikiPageRevertCommand(updated.currentRevisionId(), OWNER));

        assertThat(created.revisionNo()).isEqualTo(1);
        assertThat(updated.revisionNo()).isEqualTo(2);
        assertThat(reverted.revisionNo()).isEqualTo(3);
        assertThat(reverted.markdown()).isEqualTo("# Hello");
        assertThat(reverted.sanitizedHtml()).contains("<h1>Hello</h1>");
        assertThat(service.listRevisions(WORKSPACE_ID, "Home", PageRequest.of(0, 10), OWNER).getTotalElements())
                .isEqualTo(3);
    }

    @Test
    void rejectsMismatchedBaseRevisionId() {
        var created = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "content", null, OWNER));

        assertThatThrownBy(() -> service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "other", created.currentRevisionId() + 1, OWNER)))
                .isInstanceOf(WikiConflictException.class);
    }

    @Test
    void rejectsMissingBaseRevisionIdForExistingPageMutation() {
        var created = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "content", null, OWNER));

        assertThatThrownBy(() -> service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "other", null, OWNER)))
                .isInstanceOf(WikiConflictException.class);
        assertThatThrownBy(() -> service.revertRevision(
                WORKSPACE_ID,
                "Home",
                created.currentRevisionId(),
                new WikiPageRevertCommand(null, OWNER)))
                .isInstanceOf(WikiConflictException.class);
        assertThatThrownBy(() -> service.archivePage(
                WORKSPACE_ID,
                "Home",
                new WikiPageArchiveCommand(null, OWNER)))
                .isInstanceOf(WikiConflictException.class);
    }

    @Test
    void excludesArchivedPageFromNormalReadAndListButKeepsHistory() {
        var created = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "content", null, OWNER));

        service.archivePage(WORKSPACE_ID, "Home", new WikiPageArchiveCommand(created.currentRevisionId(), OWNER));

        assertThat(service.listPages(WORKSPACE_ID, PageRequest.of(0, 10), OWNER).getTotalElements()).isZero();
        assertThatThrownBy(() -> service.getPage(WORKSPACE_ID, "Home", OWNER))
                .isInstanceOf(WikiNotFoundException.class);
        assertThat(service.listRevisions(WORKSPACE_ID, "Home", PageRequest.of(0, 10), OWNER).getTotalElements())
                .isEqualTo(1);
        assertThat(service.getRevision(WORKSPACE_ID, "Home", created.currentRevisionId(), OWNER).markdown())
                .isEqualTo("content");
    }

    @Test
    void sidebarWritesRequireWikiAdminPermission() {
        service.putPage(
                WORKSPACE_ID,
                "_Sidebar",
                new WikiPageWriteCommand("_Sidebar", "links", null, OWNER));

        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.ADMIN));
    }

    @Test
    void footerWritesRequireWikiAdminPermission() {
        service.putPage(
                WORKSPACE_ID,
                "_Footer",
                new WikiPageWriteCommand("_Footer", "footer", null, OWNER));

        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.ADMIN));
    }

    @Test
    void reservedPageArchiveRequiresWikiAdminPermission() {
        var created = service.putPage(
                WORKSPACE_ID,
                "_Footer",
                new WikiPageWriteCommand("_Footer", "footer", null, OWNER));
        clearInvocations(permissionService);

        service.archivePage(
                WORKSPACE_ID,
                "_Footer",
                new WikiPageArchiveCommand(created.currentRevisionId(), OWNER));

        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.ADMIN));
    }

    @Test
    void reservedPageRevertRequiresWikiAdminPermission() {
        var created = service.putPage(
                WORKSPACE_ID,
                "_Sidebar",
                new WikiPageWriteCommand("_Sidebar", "links", null, OWNER));
        var updated = service.putPage(
                WORKSPACE_ID,
                "_Sidebar",
                new WikiPageWriteCommand("_Sidebar", "updated", created.currentRevisionId(), OWNER));
        var firstRevisionId = service.listRevisions(WORKSPACE_ID, "_Sidebar", PageRequest.of(0, 10), OWNER)
                .getContent()
                .stream()
                .filter(revision -> revision.revisionNo() == 1)
                .findFirst()
                .orElseThrow()
                .revisionId();
        clearInvocations(permissionService);

        service.revertRevision(
                WORKSPACE_ID,
                "_Sidebar",
                firstRevisionId,
                new WikiPageRevertCommand(updated.currentRevisionId(), OWNER));

        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.ADMIN));
    }

    @Test
    void wikiReadAndWriteUseWorkspacePermissionService() {
        var created = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "content", null, OWNER));

        service.getPage(WORKSPACE_ID, "Home", OWNER);
        service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "updated", created.currentRevisionId(), OWNER));

        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.PAGE_CREATE));
        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.PAGE_READ));
        verify(permissionService).assertGranted(eq(WORKSPACE_ID), eq(OWNER), eq(WikiPermissionActions.PAGE_UPDATE));
    }

    @Test
    void rendererStripsUnsafeHtml() {
        var page = service.putPage(
                WORKSPACE_ID,
                "Home",
                new WikiPageWriteCommand("Home", "<script>alert(1)</script>[x](javascript:alert(1))", null, OWNER));

        assertThat(page.sanitizedHtml()).doesNotContain("<script>").doesNotContain("href=\"javascript:");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = WikiPageEntity.class)
    @EnableJpaRepositories(basePackageClasses = WikiPageJpaRepository.class)
    static class TestApplication {
    }
}
