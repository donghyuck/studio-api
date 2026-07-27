package studio.one.platform.workspace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.access.AccessDeniedException;

import studio.one.base.user.domain.model.company.CompanyMemberRef;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.domain.model.company.CompanyPermissionPolicyRef;
import studio.one.base.user.domain.model.company.CompanyPermissionRolePolicyRef;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.domain.error.UserNotFoundException;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.workspace.application.error.WorkspaceConflictException;
import studio.one.platform.workspace.application.error.WorkspaceNotFoundException;
import studio.one.platform.workspace.application.error.WorkspaceValidationException;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionContributor;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.domain.model.WorkspaceRolePermissionMapping;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.application.command.ChangeWorkspaceParentCommand;
import studio.one.platform.workspace.application.command.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.application.command.CreateWorkspaceCommand;
import studio.one.platform.workspace.application.command.UpdateWorkspaceCommand;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.command.WorkspaceListQuery;
import studio.one.platform.workspace.application.command.WorkspaceMemberCommand;
import studio.one.platform.workspace.application.command.WorkspaceMemberListQuery;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;

@DataJpaTest
@ContextConfiguration(classes = DefaultWorkspaceServiceTest.Config.class)
class DefaultWorkspaceServiceTest {

    private static final WorkspaceAccessContext OWNER = new WorkspaceAccessContext(1L, "owner", false);
    private static final WorkspaceAccessContext EDITOR = new WorkspaceAccessContext(2L, "editor", false);
    private static final WorkspaceAccessContext VIEWER = new WorkspaceAccessContext(3L, "viewer", false);
    private static final WorkspaceAccessContext PLATFORM_ADMIN = new WorkspaceAccessContext(99L, "admin", true);
    private static final Map<Long, TestUser> TEST_USERS = new ConcurrentHashMap<>();

    @jakarta.annotation.Resource
    private WorkspaceTreeService treeService;

    @jakarta.annotation.Resource
    private WorkspaceMemberService memberService;

    @jakarta.annotation.Resource
    private WorkspacePermissionService permissionService;

    @jakarta.annotation.Resource
    private WorkspaceClosureJpaRepository closureRepository;

    @jakarta.annotation.Resource
    private WorkspaceJpaRepository workspaceRepository;

    @jakarta.annotation.Resource
    private WorkspaceMemberJpaRepository memberRepository;

    @jakarta.annotation.Resource
    private EntityManager entityManager;

    @BeforeEach
    void clearTestUsers() {
        TEST_USERS.clear();
    }

    @Test
    void createsRootAndChildWithPathClosureAndOwner() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));

        assertThat(root.companyId()).isNull();
        assertThat(root.path()).isEqualTo("acme");
        assertThat(child.companyId()).isNull();
        assertThat(child.path()).isEqualTo("acme/engineering");
        assertThat(child.rootId()).isEqualTo(root.id());
        assertThat(closureRepository.findAncestorIds(child.id())).containsExactly(root.id(), child.id());
        assertThat(memberService.getDirectMembers(root.id(), OWNER))
                .containsExactly(new studio.one.platform.workspace.domain.model.WorkspaceMemberRef(
                        root.id(),
                        OWNER.userId(),
                        WorkspaceRole.OWNER,
                        false));
    }

    @Test
    void rootCanBeScopedToCompanyAndChildInheritsCompany() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));

        assertThat(root.companyId()).isEqualTo(10L);
        assertThat(child.companyId()).isEqualTo(10L);
        assertThat(treeService.getByPath(10L, "acme/engineering", OWNER).id()).isEqualTo(child.id());
        assertThatThrownBy(() -> treeService.getByPath(20L, "acme/engineering", PLATFORM_ADMIN))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void rootSlugUniquenessIsScopedByCompanyOnlyAfterCompanyScopeEnforcement() {
        WorkspaceTreeService companyScopeEnforcedTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                new WorkspaceSettings(10, 200, 100, true, true, true),
                companyService());

        var acme = companyScopeEnforcedTreeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "portal",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var contoso = companyScopeEnforcedTreeService.createRoot(new CreateRootWorkspaceCommand(
                20L,
                "Contoso",
                "portal",
                WorkspaceVisibility.PRIVATE,
                OWNER));

        assertThat(acme.slug()).isEqualTo("portal");
        assertThat(contoso.slug()).isEqualTo("portal");
        assertThat(companyScopeEnforcedTreeService.getByPath(10L, "portal", OWNER).id()).isEqualTo(acme.id());
        assertThat(companyScopeEnforcedTreeService.getByPath(20L, "portal", OWNER).id()).isEqualTo(contoso.id());
        assertThatThrownBy(() -> companyScopeEnforcedTreeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme Duplicate",
                "portal",
                WorkspaceVisibility.PRIVATE,
                OWNER)))
                .isInstanceOf(WorkspaceConflictException.class);
    }

    @Test
    void companyScopedPublicWorkspaceDoesNotGrantImplicitViewerAcrossCompanies() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PUBLIC,
                OWNER));
        var authenticated = new WorkspaceAccessContext(77L, "authenticated", false);

        assertThatThrownBy(() -> treeService.getById(root.id(), authenticated))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void companyRequiredRejectsLegacyRootCreationWithoutCompanyId() {
        WorkspaceTreeService companyRequiredTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                (WorkspaceMemberJpaRepository) null,
                permissionService,
                new WorkspaceSettings(10, 200, 100, true, true, false));

        assertThatThrownBy(() -> companyRequiredTreeService.createRoot(createCommand("Acme", "acme", OWNER)))
                .isInstanceOf(WorkspaceValidationException.class);
    }

    @Test
    void companyRequiredRejectsMissingCompanyIdBeforePersistingRoot() {
        ApplicationCompanyService missingCompanyService = org.mockito.Mockito.mock(ApplicationCompanyService.class);
        org.mockito.Mockito.when(missingCompanyService.get(404L)).thenThrow(NotFoundException.of("company", 404L));
        WorkspaceTreeService companyRequiredTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                new WorkspaceSettings(10, 200, 100, true, true, true),
                missingCompanyService);
        long before = workspaceRepository.count();

        assertThatThrownBy(() -> companyRequiredTreeService.createRoot(new CreateRootWorkspaceCommand(
                404L,
                "Missing Company",
                "missing-company",
                WorkspaceVisibility.PRIVATE,
                OWNER)))
                .isInstanceOf(NotFoundException.class);
        assertThat(workspaceRepository.count()).isEqualTo(before);
    }

    @Test
    void companyRequiredRejectsMovingLegacyWorkspaceToRoot() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        WorkspaceTreeService companyRequiredTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                (WorkspaceMemberJpaRepository) null,
                permissionService,
                new WorkspaceSettings(10, 200, 100, true, true, false));

        assertThatThrownBy(() -> companyRequiredTreeService.changeParent(
                child.id(),
                new ChangeWorkspaceParentCommand(null, OWNER)))
                .isInstanceOf(WorkspaceValidationException.class);
    }

    @Test
    void rejectsDuplicateSlugUnderSameParentButAllowsDifferentParent() {
        var root = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        var design = treeService.createChild(root.id(), createCommand("Design", "design", OWNER));

        assertThatThrownBy(() -> treeService.createChild(root.id(), createCommand("Engineering 2", "engineering", OWNER)))
                .isInstanceOf(WorkspaceConflictException.class);

        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        var designBackend = treeService.createChild(design.id(), createCommand("Backend", "backend", OWNER));
        assertThat(backend.path()).isEqualTo("acme/engineering/backend");
        assertThat(designBackend.path()).isEqualTo("acme/design/backend");
    }

    @Test
    void inheritsRoleAndUsesStrongestEffectiveRole() {
        var root = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        memberService.addMember(root.id(), new WorkspaceMemberCommand(EDITOR.userId(), WorkspaceRole.EDITOR, OWNER));
        memberService.addMember(engineering.id(), new WorkspaceMemberCommand(EDITOR.userId(), WorkspaceRole.VIEWER, OWNER));

        assertThat(permissionService.getEffectiveRole(backend.id(), EDITOR)).isEqualTo(WorkspaceRole.EDITOR);
        treeService.update(backend.id(), new UpdateWorkspaceCommand("Backend Platform", WorkspaceVisibility.PRIVATE, EDITOR));
        assertThat(treeService.getById(backend.id(), EDITOR).name()).isEqualTo("Backend Platform");
    }

    @Test
    void effectiveMembersPreferDirectMembershipWhenRoleRankTies() {
        var root = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        memberService.addMember(root.id(), new WorkspaceMemberCommand(EDITOR.userId(), WorkspaceRole.VIEWER, OWNER));
        memberService.addMember(engineering.id(), new WorkspaceMemberCommand(EDITOR.userId(), WorkspaceRole.VIEWER, OWNER));

        assertThat(memberService.getEffectiveMembers(engineering.id(), OWNER))
                .filteredOn(member -> member.userId().equals(EDITOR.userId()))
                .singleElement()
                .satisfies(member -> assertThat(member.inherited()).isFalse());
    }

    @Test
    void directMembersSupportServerPagingRoleAndKeywordSearch() {
        var root = createRoot("Acme", "acme", OWNER);
        insertUser(20L, "alice", "Alice Park", "alice@example.com");
        insertUser(21L, "bob", "Bob Lee", "bob@example.com");
        insertUser(22L, "carol", "Carol Kim", "carol@example.com");
        memberService.addMember(root.id(), new WorkspaceMemberCommand(20L, WorkspaceRole.EDITOR, OWNER));
        memberService.addMember(root.id(), new WorkspaceMemberCommand(21L, WorkspaceRole.VIEWER, OWNER));
        memberService.addMember(root.id(), new WorkspaceMemberCommand(22L, WorkspaceRole.VIEWER, OWNER));

        var secondPage = memberService.getDirectMembers(
                root.id(),
                WorkspaceMemberListQuery.all(),
                PageRequest.of(1, 2, Sort.by("userId").ascending()),
                OWNER);
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(21L, 22L);

        var viewerPage = memberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery(null, WorkspaceRole.VIEWER, null),
                PageRequest.of(0, 10, Sort.by("userId").ascending()),
                OWNER);
        assertThat(viewerPage.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(21L, 22L);

        var keywordPage = memberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery("alice", null, null),
                PageRequest.of(0, 10),
                OWNER);
        assertThat(keywordPage.getTotalElements()).isOne();
        assertThat(keywordPage.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(20L);

        assertThat(memberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery(null, null, true),
                PageRequest.of(0, 10),
                OWNER).getTotalElements()).isZero();
    }

    @Test
    void directMemberKeywordSearchFallsBackToDatabaseWithoutUserService() {
        var root = createRoot("Acme", "acme", OWNER);
        WorkspaceMemberService numericOnlyMemberService = new DefaultWorkspaceMemberService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                entityManager,
                null);
        insertUser(123L, "alice", "Alice Park", "alice@example.com");
        memberService.addMember(root.id(), new WorkspaceMemberCommand(123L, WorkspaceRole.EDITOR, OWNER));
        memberService.addMember(root.id(), new WorkspaceMemberCommand(456L, WorkspaceRole.VIEWER, OWNER));

        var numericKeyword = numericOnlyMemberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery("123", null, null),
                PageRequest.of(0, 10),
                OWNER);
        var textKeyword = numericOnlyMemberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery("alice", null, null),
                PageRequest.of(0, 10),
                OWNER);

        assertThat(numericKeyword.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(123L);
        assertThat(textKeyword.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(123L);
    }

    @Test
    void directMemberKeywordSearchFindsWorkspaceMemberBeyondFirstUserSearchPage() {
        var root = createRoot("Acme", "acme", OWNER);
        for (long userId = 1000; userId <= 1200; userId++) {
            insertUser(userId, "alice-" + userId, "Alice " + userId, "alice-" + userId + "@example.com");
        }
        insertUser(1201L, "alice-target", "Alice Target", "alice-target@example.com");
        memberService.addMember(root.id(), new WorkspaceMemberCommand(1201L, WorkspaceRole.EDITOR, OWNER));

        var keywordPage = memberService.getDirectMembers(
                root.id(),
                new WorkspaceMemberListQuery("alice", null, null),
                PageRequest.of(0, 10),
                OWNER);

        assertThat(keywordPage.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .containsExactly(1201L);
    }

    @Test
    void effectiveMembersSupportServerPagingInheritedAndKeywordSearch() {
        var root = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        insertUser(20L, "alice", "Alice Park", "alice@example.com");
        insertUser(21L, "bob", "Bob Lee", "bob@example.com");
        memberService.addMember(root.id(), new WorkspaceMemberCommand(20L, WorkspaceRole.EDITOR, OWNER));
        memberService.addMember(backend.id(), new WorkspaceMemberCommand(21L, WorkspaceRole.VIEWER, OWNER));

        var inheritedPage = memberService.getEffectiveMembers(
                backend.id(),
                new WorkspaceMemberListQuery(null, null, true),
                PageRequest.of(0, 10, Sort.by("userId").ascending()),
                OWNER);
        assertThat(inheritedPage.getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceMemberRef::userId)
                .contains(OWNER.userId(), 20L)
                .doesNotContain(21L);

        var keywordPage = memberService.getEffectiveMembers(
                backend.id(),
                new WorkspaceMemberListQuery("bob", null, false),
                PageRequest.of(0, 10),
                OWNER);
        assertThat(keywordPage.getTotalElements()).isOne();
        assertThat(keywordPage.getContent().get(0).userId()).isEqualTo(21L);
        assertThat(keywordPage.getContent().get(0).inherited()).isFalse();
    }

    @Test
    void viewerCannotUpdateButPlatformAdminBypassesMembership() {
        var root = createRoot("Acme", "acme", OWNER);
        memberService.addMember(root.id(), new WorkspaceMemberCommand(VIEWER.userId(), WorkspaceRole.VIEWER, OWNER));

        assertThatThrownBy(() -> treeService.update(
                root.id(),
                new UpdateWorkspaceCommand("New Name", WorkspaceVisibility.PRIVATE, VIEWER)))
                .isInstanceOf(AccessDeniedException.class);

        treeService.update(root.id(), new UpdateWorkspaceCommand("Admin Name", WorkspaceVisibility.PRIVATE, PLATFORM_ADMIN));
        assertThat(treeService.getById(root.id(), PLATFORM_ADMIN).name()).isEqualTo("Admin Name");
    }

    @Test
    void updatePreservesOmittedPatchFields() {
        var root = treeService.createRoot(new CreateWorkspaceCommand(
                "Acme",
                "acme",
                WorkspaceVisibility.PUBLIC,
                OWNER));

        treeService.update(root.id(), new UpdateWorkspaceCommand("Renamed", null, OWNER));
        assertThat(treeService.getById(root.id(), OWNER).visibility()).isEqualTo(WorkspaceVisibility.PUBLIC);

        treeService.update(root.id(), new UpdateWorkspaceCommand(null, WorkspaceVisibility.INTERNAL, OWNER));
        var updated = treeService.getById(root.id(), OWNER);
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.visibility()).isEqualTo(WorkspaceVisibility.INTERNAL);
    }

    @Test
    void changeParentMovesSubtreeAndRebuildsClosure() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        var beta = createRoot("Beta", "beta", OWNER);

        var moved = treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(beta.id(), OWNER));

        assertThat(moved.parentId()).isEqualTo(beta.id());
        assertThat(moved.rootId()).isEqualTo(beta.id());
        assertThat(moved.path()).isEqualTo("beta/engineering");
        assertThat(moved.depth()).isEqualTo(1);

        var movedBackend = treeService.getById(backend.id(), OWNER);
        assertThat(movedBackend.parentId()).isEqualTo(engineering.id());
        assertThat(movedBackend.rootId()).isEqualTo(beta.id());
        assertThat(movedBackend.path()).isEqualTo("beta/engineering/backend");
        assertThat(movedBackend.depth()).isEqualTo(2);
        assertThat(closureRepository.findAncestorIds(backend.id()))
                .containsExactly(beta.id(), engineering.id(), backend.id());
    }

    @Test
    void changeParentRebuildsMissingClosureRowsFromParentTree() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        var beta = createRoot("Beta", "beta", OWNER);
        closureRepository.deleteAll();

        var moved = treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(beta.id(), PLATFORM_ADMIN));

        assertThat(moved.parentId()).isEqualTo(beta.id());
        assertThat(moved.rootId()).isEqualTo(beta.id());
        assertThat(moved.path()).isEqualTo("beta/engineering");
        assertThat(treeService.getById(backend.id(), PLATFORM_ADMIN).path())
                .isEqualTo("beta/engineering/backend");
        assertThat(closureRepository.findAncestorIds(beta.id()))
                .containsExactly(beta.id());
        assertThat(closureRepository.findAncestorIds(backend.id()))
                .containsExactly(beta.id(), engineering.id(), backend.id());
        assertThat(treeService.getTree(beta.id(), PLATFORM_ADMIN).children())
                .extracting(node -> node.workspace().id())
                .containsExactly(engineering.id());
    }

    @Test
    void changeParentMovesRootLeafWhenClosureRowsAreMissing() {
        var acme = createRoot("Acme", "acme", OWNER);
        var beta = createRoot("Beta", "beta", OWNER);
        closureRepository.deleteAll();

        var moved = treeService.changeParent(
                acme.id(),
                new ChangeWorkspaceParentCommand(beta.id(), PLATFORM_ADMIN));

        assertThat(moved.parentId()).isEqualTo(beta.id());
        assertThat(moved.rootId()).isEqualTo(beta.id());
        assertThat(moved.path()).isEqualTo("beta/acme");
        assertThat(moved.depth()).isEqualTo(1);
        assertThat(closureRepository.findAncestorIds(acme.id()))
                .containsExactly(beta.id(), acme.id());
        assertThat(treeService.getTree(beta.id(), PLATFORM_ADMIN).children())
                .extracting(node -> node.workspace().id())
                .containsExactly(acme.id());
    }

    @Test
    void changeParentAllowsMovingWorkspaceToRoot() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));

        var moved = treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(null, OWNER));

        assertThat(moved.parentId()).isNull();
        assertThat(moved.rootId()).isEqualTo(engineering.id());
        assertThat(moved.path()).isEqualTo("engineering");
        assertThat(moved.depth()).isZero();
        assertThat(closureRepository.findAncestorIds(engineering.id()))
                .containsExactly(engineering.id());
    }

    @Test
    void changeParentRejectsCyclesAndDuplicateSiblingSlug() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var backend = treeService.createChild(engineering.id(), createCommand("Backend", "backend", OWNER));
        var design = treeService.createChild(acme.id(), createCommand("Design", "design", OWNER));
        treeService.createChild(design.id(), createCommand("Backend", "backend", OWNER));

        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(backend.id(), OWNER)))
                .isInstanceOf(WorkspaceConflictException.class);
        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(engineering.id(), OWNER)))
                .isInstanceOf(WorkspaceConflictException.class);
        assertThatThrownBy(() -> treeService.changeParent(
                backend.id(),
                new ChangeWorkspaceParentCommand(design.id(), OWNER)))
                .isInstanceOf(WorkspaceConflictException.class);
    }

    @Test
    void changeParentEnforcesPermissionAndParentExistence() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var beta = createRoot("Beta", "beta", OWNER);
        memberService.addMember(acme.id(), new WorkspaceMemberCommand(VIEWER.userId(), WorkspaceRole.VIEWER, OWNER));

        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(beta.id(), VIEWER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(999L, OWNER)))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void changeParentRejectsCrossCompanyMove() {
        var acme = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var beta = treeService.createRoot(new CreateRootWorkspaceCommand(
                20L,
                "Beta",
                "beta",
                WorkspaceVisibility.PRIVATE,
                OWNER));

        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(beta.id(), PLATFORM_ADMIN)))
                .isInstanceOf(WorkspaceConflictException.class);
    }

    @Test
    void changeParentRejectsArchivedWorkspaceMutation() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var beta = createRoot("Beta", "beta", OWNER);
        treeService.archive(engineering.id(), OWNER);

        assertThatThrownBy(() -> treeService.changeParent(
                engineering.id(),
                new ChangeWorkspaceParentCommand(beta.id(), OWNER)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(workspaceRepository.findById(engineering.id()).orElseThrow().getParentId())
                .isEqualTo(acme.id());
    }

    @Test
    void treeListsHidePrivateChildrenWithoutRole() {
        var root = treeService.createRoot(new CreateWorkspaceCommand(
                "Acme",
                "acme",
                WorkspaceVisibility.PUBLIC,
                OWNER));
        var privateChild = treeService.createChild(
                root.id(),
                new CreateWorkspaceCommand("Secret", "secret", WorkspaceVisibility.PRIVATE, OWNER));
        treeService.createChild(
                privateChild.id(),
                new CreateWorkspaceCommand("Public Grandchild", "public-grandchild", WorkspaceVisibility.PUBLIC, OWNER));
        var authenticated = new WorkspaceAccessContext(77L, "authenticated", false);

        assertThat(treeService.getChildren(root.id(), authenticated)).isEmpty();
        assertThat(treeService.getDescendants(root.id(), authenticated)).isEmpty();
        assertThat(treeService.getTree(root.id(), authenticated).children()).isEmpty();
        assertThatThrownBy(() -> treeService.getById(privateChild.id(), authenticated))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managementListFiltersAndPagesWorkspaces() {
        var acme = createRoot("Acme", "acme", OWNER);
        var engineering = treeService.createChild(acme.id(), createCommand("Engineering", "engineering", OWNER));
        var design = treeService.createChild(acme.id(), createCommand("Design", "design", OWNER));
        var beta = createRoot("Beta", "beta", OWNER);
        treeService.archive(design.id(), OWNER);

        var sortedByPath = PageRequest.of(0, 10, Sort.by("path").ascending());

        assertThat(treeService.list(new WorkspaceListQuery(null, null, true, null), sortedByPath, PLATFORM_ADMIN)
                .getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                .containsExactly(acme.id(), beta.id());
        assertThat(treeService.list(new WorkspaceListQuery(null, acme.id(), false, false), sortedByPath, PLATFORM_ADMIN)
                .getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                .containsExactly(engineering.id());
        assertThat(treeService.list(new WorkspaceListQuery("ACME/ENG", null, null, null), sortedByPath, PLATFORM_ADMIN)
                .getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                .containsExactly(engineering.id());
        assertThat(treeService.list(new WorkspaceListQuery(null, null, null, true), sortedByPath, PLATFORM_ADMIN)
                .getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                .containsExactly(design.id());
        assertThat(treeService.list(new WorkspaceListQuery(null, null, null, null), PageRequest.of(0, 2, Sort.by("id").descending()), PLATFORM_ADMIN)
                .getContent())
                .extracting(studio.one.platform.workspace.domain.model.WorkspaceRef::id)
                .containsExactly(beta.id(), design.id());
    }

    @Test
    void managementListRejectsInvalidCompanyIdFilter() {
        assertThatThrownBy(() -> treeService.list(
                new WorkspaceListQuery(null, 0L, null, null, null),
                PageRequest.of(0, 10),
                PLATFORM_ADMIN))
                .isInstanceOf(WorkspaceValidationException.class);
    }

    @Test
    void managementListRequiresPlatformAdminContext() {
        createRoot("Acme", "acme", OWNER);

        assertThatThrownBy(() -> treeService.list(
                new WorkspaceListQuery(null, null, null, null),
                PageRequest.of(0, 10),
                OWNER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ancestorsHidePrivateParentsWithoutRole() {
        var root = treeService.createRoot(new CreateWorkspaceCommand(
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var child = treeService.createChild(
                root.id(),
                new CreateWorkspaceCommand("Child", "child", WorkspaceVisibility.PRIVATE, OWNER));
        memberService.addMember(child.id(), new WorkspaceMemberCommand(VIEWER.userId(), WorkspaceRole.VIEWER, OWNER));

        assertThat(treeService.getAncestors(child.id(), VIEWER)).isEmpty();
    }

    @Test
    void archiveRejectsFurtherMutation() {
        var root = createRoot("Acme", "acme", OWNER);
        treeService.archive(root.id(), OWNER);

        assertThat(treeService.getById(root.id(), OWNER).archived()).isTrue();
        assertThat(permissionService.getGrantedActions(root.id(), OWNER))
                .contains("workspace.read", "workspace.tree.read")
                .doesNotContain("workspace.update", "workspace.member.manage");
        assertThatThrownBy(() -> treeService.update(
                root.id(),
                new UpdateWorkspaceCommand("New Name", WorkspaceVisibility.PRIVATE, OWNER)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void archiveRequiresCascadeWhenWorkspaceHasActiveDescendants() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));

        assertThatThrownBy(() -> treeService.archive(root.id(), OWNER, false))
                .isInstanceOf(WorkspaceConflictException.class);

        treeService.archive(root.id(), OWNER, true);

        assertThat(treeService.getById(root.id(), OWNER).archived()).isTrue();
        assertThat(treeService.getById(child.id(), OWNER).archived()).isTrue();
    }

    @Test
    void cascadeArchiveRequiresArchivePermissionOnDescendants() {
        var root = createRoot("Acme", "acme", OWNER);
        treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        WorkspacePermissionService nonInheritedPermissionService = new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                List.of(wikiLikeContributor()),
                new WorkspaceSettings(10, 200, 100, false, false, false));
        WorkspaceTreeService nonInheritedTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                nonInheritedPermissionService,
                new WorkspaceSettings(10, 200, 100, false, false, false));

        assertThatThrownBy(() -> nonInheritedTreeService.archive(root.id(), OWNER, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activateRestoresWorkspaceAndCascadeRestoresDescendants() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        treeService.archive(root.id(), OWNER, true);

        var activated = treeService.activate(root.id(), OWNER, true);

        assertThat(activated.archived()).isFalse();
        assertThat(treeService.getById(child.id(), OWNER).archived()).isFalse();
    }

    @Test
    void activateWithoutCascadeLeavesArchivedDescendantsArchived() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        treeService.archive(root.id(), OWNER, true);

        var activated = treeService.activate(root.id(), OWNER, false);

        assertThat(activated.archived()).isFalse();
        assertThat(treeService.getById(child.id(), OWNER).archived()).isTrue();
    }

    @Test
    void cascadeActivateRequiresActivatePermissionOnDescendants() {
        var root = createRoot("Acme", "acme", OWNER);
        treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        treeService.archive(root.id(), OWNER, true);
        WorkspacePermissionService nonInheritedPermissionService = new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                List.of(wikiLikeContributor()),
                new WorkspaceSettings(10, 200, 100, false, false, false));
        WorkspaceTreeService nonInheritedTreeService = new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                nonInheritedPermissionService,
                new WorkspaceSettings(10, 200, 100, false, false, false));

        assertThatThrownBy(() -> nonInheritedTreeService.activate(root.id(), OWNER, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void activateRejectsArchivedAncestor() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));
        treeService.archive(root.id(), OWNER, true);

        assertThatThrownBy(() -> treeService.activate(child.id(), OWNER, false))
                .isInstanceOf(WorkspaceConflictException.class);
    }

    @Test
    void activateAlreadyActiveWorkspaceIsIdempotent() {
        var root = createRoot("Acme", "acme", OWNER);

        assertThat(treeService.activate(root.id(), OWNER, false).archived()).isFalse();
    }

    @Test
    void archivedWorkspaceAllowsContributedReadActionsOnly() {
        var root = createRoot("Acme", "acme", OWNER);
        treeService.archive(root.id(), OWNER);

        assertThat(permissionService.isGranted(root.id(), OWNER, "wiki.page.read")).isTrue();
        assertThat(permissionService.isGranted(root.id(), OWNER, "wiki.page.history.read")).isTrue();
        assertThat(permissionService.isGranted(root.id(), OWNER, "wiki.page.update")).isFalse();
        assertThat(permissionService.isGranted(root.id(), OWNER, "workspace.activate")).isTrue();
        assertThat(permissionService.getGrantedActions(root.id(), OWNER))
                .contains("wiki.page.read", "wiki.page.history.read", "workspace.activate")
                .doesNotContain("wiki.page.update");
    }

    @Test
    void companyOwnerOverrideGrantsWorkspaceAndWikiActionsWhenWorkspaceRoleIsMissing() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var companyOwner = new WorkspaceAccessContext(50L, "company-owner", false);
        WorkspacePermissionService permissions = permissionServiceWithCompanyRole(10L, 50L, CompanyRole.OWNER);

        assertThat(permissions.getEffectiveRole(root.id(), companyOwner)).isNull();
        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.update")).isTrue();
        assertThat(permissions.isGranted(root.id(), companyOwner, "wiki.page.update")).isTrue();
    }

    @Test
    void companyOwnerOverrideExpandsActionsWithoutRewritingEffectiveWorkspaceRole() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var companyOwner = new WorkspaceAccessContext(50L, "company-owner", false);
        memberService.addMember(root.id(), new WorkspaceMemberCommand(50L, WorkspaceRole.VIEWER, OWNER));
        WorkspacePermissionService permissions = permissionServiceWithCompanyRole(10L, 50L, CompanyRole.OWNER);

        assertThat(permissions.getEffectiveRole(root.id(), companyOwner)).isEqualTo(WorkspaceRole.VIEWER);
        assertThat(permissions.getGrantedActions(root.id(), companyOwner))
                .contains("workspace.update", "workspace.member.manage", "wiki.page.update");
    }

    @Test
    void companyAdminDoesNotOverridePrivateWorkspaceOrWikiRead() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var companyAdmin = new WorkspaceAccessContext(51L, "company-admin", false);
        WorkspacePermissionService permissions = permissionServiceWithCompanyRole(10L, 51L, CompanyRole.ADMIN);

        assertThat(permissions.getEffectiveRole(root.id(), companyAdmin)).isNull();
        assertThat(permissions.isGranted(root.id(), companyAdmin, "workspace.read")).isFalse();
        assertThat(permissions.isGranted(root.id(), companyAdmin, "wiki.page.read")).isFalse();
    }

    @Test
    void archivedWorkspaceBlocksCompanyOwnerMutationOverride() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        treeService.archive(root.id(), OWNER);
        var companyOwner = new WorkspaceAccessContext(50L, "company-owner", false);
        WorkspacePermissionService permissions = permissionServiceWithCompanyRole(10L, 50L, CompanyRole.OWNER);

        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.read")).isTrue();
        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.update")).isFalse();
        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.activate")).isTrue();
        assertThat(permissions.isGranted(root.id(), companyOwner, "wiki.page.update")).isFalse();
    }

    @Test
    void companyOwnerOverrideHonorsCompanyWorkspacePolicy() {
        var root = treeService.createRoot(new CreateRootWorkspaceCommand(
                10L,
                "Acme",
                "acme",
                WorkspaceVisibility.PRIVATE,
                OWNER));
        var companyOwner = new WorkspaceAccessContext(50L, "company-owner", false);
        WorkspacePermissionService permissions = new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                List.of(wikiLikeContributor()),
                WorkspaceSettings.defaults(),
                companyMemberService(10L, 50L, CompanyRole.OWNER),
                companyPermissionService(10L, 50L, CompanyPermissionActions.WORKSPACE_READ));

        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.read")).isTrue();
        assertThat(permissions.isGranted(root.id(), companyOwner, "workspace.update")).isFalse();
        assertThat(permissions.isGranted(root.id(), companyOwner, "wiki.page.read")).isTrue();
        assertThat(permissions.isGranted(root.id(), companyOwner, "wiki.page.update")).isFalse();
        assertThat(permissions.getGrantedActions(root.id(), companyOwner))
                .contains("workspace.read", "wiki.page.read")
                .doesNotContain("workspace.update", "wiki.page.update");
    }

    private studio.one.platform.workspace.domain.model.WorkspaceRef createRoot(
            String name,
            String slug,
            WorkspaceAccessContext actor) {
        return treeService.createRoot(createCommand(name, slug, actor));
    }

    private CreateWorkspaceCommand createCommand(String name, String slug, WorkspaceAccessContext actor) {
        return new CreateWorkspaceCommand(name, slug, WorkspaceVisibility.PRIVATE, actor);
    }

    private WorkspacePermissionService permissionServiceWithCompanyRole(
            Long companyId,
            Long userId,
            CompanyRole role) {
        return new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                List.of(wikiLikeContributor()),
                WorkspaceSettings.defaults(),
                companyMemberService(companyId, userId, role));
    }

    private ApplicationCompanyMemberService companyMemberService(Long companyId, Long userId, CompanyRole role) {
        return new ApplicationCompanyMemberService() {
            @Override
            public CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeMember(Long companyId, Long userId, Long actorUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeMember(Long companyId, Long userId, Long actorUserId, boolean bypassRoleLimit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyMemberRef getMember(Long companyId, Long userId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Page<CompanyMemberRef> getMembers(Long companyId, Pageable pageable) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<CompanyMemberRef> getMembers(Long companyId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isCompanyMember(Long companyId, Long userId) {
                return getCompanyRole(companyId, userId) != null;
            }

            @Override
            public CompanyRole getCompanyRole(Long requestedCompanyId, Long requestedUserId) {
                return companyId.equals(requestedCompanyId) && userId.equals(requestedUserId) ? role : null;
            }
        };
    }

    private ApplicationCompanyPermissionService companyPermissionService(Long companyId, Long userId, String... grantedActions) {
        java.util.Set<String> granted = java.util.Set.of(grantedActions);
        return new ApplicationCompanyPermissionService() {
            @Override
            public boolean isGranted(Long requestedCompanyId, Long requestedUserId, String action) {
                return companyId.equals(requestedCompanyId) && userId.equals(requestedUserId) && granted.contains(action);
            }

            @Override
            public void assertGranted(Long companyId, Long userId, String action) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> getGrantedActions(Long companyId, Long userId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyPermissionPolicyRef getPolicy(Long companyId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompanyPermissionPolicyRef updatePolicy(
                    Long companyId,
                    List<CompanyPermissionRolePolicyRef> roles,
                    Long actorUserId,
                    boolean platformAdmin) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static WorkspacePermissionContributor wikiLikeContributor() {
        return new WorkspacePermissionContributor() {
            @Override
            public List<WorkspacePermissionDefinition> permissions() {
                return List.of(
                        new WorkspacePermissionDefinition("wiki.page.read", "Read wiki pages"),
                        new WorkspacePermissionDefinition("wiki.page.history.read", "Read wiki page history"),
                        new WorkspacePermissionDefinition("wiki.page.update", "Update wiki pages"));
            }

            @Override
            public List<WorkspaceRolePermissionMapping> defaultMappings() {
                return List.of(
                        new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, "wiki.page.read"),
                        new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, "wiki.page.history.read"),
                        new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, "wiki.page.update"));
            }
        };
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = {
            WorkspaceClosureEntity.class,
            DefaultWorkspaceServiceTest.TestApplicationUserEntity.class })
    @EnableJpaRepositories(basePackageClasses = {
            WorkspaceJpaRepository.class,
            WorkspaceClosureJpaRepository.class,
            WorkspaceMemberJpaRepository.class })
    static class Config {
        @Bean
        WorkspaceSettings workspaceSettings() {
            return WorkspaceSettings.defaults();
        }

        @Bean
        WorkspacePermissionService workspacePermissionService(
                WorkspaceJpaRepository workspaceRepository,
                WorkspaceClosureJpaRepository closureRepository,
                WorkspaceMemberJpaRepository memberRepository,
                WorkspaceSettings settings) {
            return new DefaultWorkspacePermissionService(
                    workspaceRepository,
                    closureRepository,
                    memberRepository,
                    List.of(wikiLikeContributor()),
                    settings);
        }

        @Bean
        WorkspacePermissionContributor wikiLikeContributor() {
            return new WorkspacePermissionContributor() {
                @Override
                public List<WorkspacePermissionDefinition> permissions() {
                    return List.of(
                            new WorkspacePermissionDefinition("wiki.page.read", "Read wiki pages"),
                            new WorkspacePermissionDefinition("wiki.page.history.read", "Read wiki page history"),
                            new WorkspacePermissionDefinition("wiki.page.update", "Update wiki pages"));
                }

                @Override
                public List<WorkspaceRolePermissionMapping> defaultMappings() {
                    return List.of(
                            new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, "wiki.page.read"),
                            new WorkspaceRolePermissionMapping(WorkspaceRole.VIEWER, "wiki.page.history.read"),
                            new WorkspaceRolePermissionMapping(WorkspaceRole.EDITOR, "wiki.page.update"));
                }
            };
        }

        @Bean
        WorkspaceTreeService workspaceTreeService(
                WorkspaceJpaRepository workspaceRepository,
                WorkspaceClosureJpaRepository closureRepository,
                WorkspaceMemberJpaRepository memberRepository,
                WorkspacePermissionService permissionService,
                WorkspaceSettings settings) {
            return new DefaultWorkspaceTreeService(
                    workspaceRepository,
                    closureRepository,
                    memberRepository,
                    permissionService,
                    settings);
        }

        @Bean
        WorkspaceMemberService workspaceMemberService(
                WorkspaceJpaRepository workspaceRepository,
                WorkspaceClosureJpaRepository closureRepository,
                WorkspaceMemberJpaRepository memberRepository,
                WorkspacePermissionService permissionService,
                EntityManager entityManager) {
            return new DefaultWorkspaceMemberService(
                    workspaceRepository,
                    closureRepository,
                    memberRepository,
                    permissionService,
                    entityManager,
                    testUserService());
        }
    }

    private void insertUser(Long userId, String username, String name, String email) {
        entityManager.createNativeQuery("delete from TB_APPLICATION_USER where USER_ID = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                insert into TB_APPLICATION_USER (USER_ID, USERNAME, NAME, EMAIL)
                values (:userId, :username, :name, :email)
                """)
                .setParameter("userId", userId)
                .setParameter("username", username)
                .setParameter("name", name)
                .setParameter("email", email)
                .executeUpdate();
        TEST_USERS.put(userId, new TestUser(userId, username, name, email));
    }

    private static ApplicationCompanyService companyService() {
        ApplicationCompanyService service = org.mockito.Mockito.mock(ApplicationCompanyService.class);
        org.mockito.Mockito.when(service.get(org.mockito.Mockito.anyLong())).thenAnswer(invocation -> {
            ApplicationCompany company = new ApplicationCompany();
            company.setCompanyId(invocation.getArgument(0));
            return company;
        });
        return service;
    }

    @SuppressWarnings("unchecked")
    private static ApplicationUserService<User, Role> testUserService() {
        return (ApplicationUserService<User, Role>) java.lang.reflect.Proxy.newProxyInstance(
                ApplicationUserService.class.getClassLoader(),
                new Class<?>[] { ApplicationUserService.class },
                (proxy, method, args) -> {
                    if ("findByNameOrUsernameOrEmail".equals(method.getName())) {
                        String keyword = String.valueOf(args[0]).toLowerCase(java.util.Locale.ROOT);
                        Pageable pageable = (Pageable) args[1];
                        List<User> users = TEST_USERS.values().stream()
                                .filter(user -> contains(user.getUsername(), keyword)
                                        || contains(user.getName(), keyword)
                                        || contains(user.getEmail(), keyword))
                                .sorted(java.util.Comparator.comparing(User::getUserId))
                                .skip(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .map(user -> (User) user)
                                .toList();
                        long total = TEST_USERS.values().stream()
                                .filter(user -> contains(user.getUsername(), keyword)
                                        || contains(user.getName(), keyword)
                                        || contains(user.getEmail(), keyword))
                                .count();
                        return new org.springframework.data.domain.PageImpl<>(users, pageable, total);
                    }
                    if ("get".equals(method.getName())) {
                        Long userId = (Long) args[0];
                        TestUser user = TEST_USERS.get(userId);
                        if (user == null) {
                            throw UserNotFoundException.byId(userId);
                        }
                        return user;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword);
    }

    record TestUser(Long userId, String username, String name, String email) implements User {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFirstName() {
            return null;
        }

        @Override
        public String getLastName() {
            return null;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isNameVisible() {
            return true;
        }

        @Override
        public boolean isEmailVisible() {
            return true;
        }

        @Override
        public Status getStatus() {
            return Status.APPROVED;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean isAnonymous() {
            return false;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public int getFailedAttempts() {
            return 0;
        }

        @Override
        public boolean isAccountLockedNow(java.time.Instant now) {
            return false;
        }

        @Override
        public java.time.Instant getLastFailedAt() {
            return null;
        }

        @Override
        public java.time.Instant getAccountLockedUntil() {
            return null;
        }

        @Override
        public java.time.Instant getCreationDate() {
            return null;
        }

        @Override
        public java.time.Instant getModifiedDate() {
            return null;
        }

        @Override
        public boolean isExternal() {
            return false;
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of();
        }

        @Override
        public void setProperties(Map<String, String> properties) {
        }
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "TB_APPLICATION_USER")
    static class TestApplicationUserEntity {
        @jakarta.persistence.Id
        @jakarta.persistence.Column(name = "USER_ID")
        private Long userId;

        @jakarta.persistence.Column(name = "USERNAME")
        private String username;

        @jakarta.persistence.Column(name = "NAME")
        private String name;

        @jakarta.persistence.Column(name = "EMAIL")
        private String email;
    }
}
