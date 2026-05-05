package studio.one.platform.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.access.AccessDeniedException;

import studio.one.platform.workspace.exception.WorkspaceConflictException;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.model.WorkspaceVisibility;
import studio.one.platform.workspace.permission.WorkspacePermissionContributor;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.service.CreateWorkspaceCommand;
import studio.one.platform.workspace.service.UpdateWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceMemberCommand;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;

@DataJpaTest
@ContextConfiguration(classes = DefaultWorkspaceServiceTest.Config.class)
class DefaultWorkspaceServiceTest {

    private static final WorkspaceAccessContext OWNER = new WorkspaceAccessContext(1L, "owner", false);
    private static final WorkspaceAccessContext EDITOR = new WorkspaceAccessContext(2L, "editor", false);
    private static final WorkspaceAccessContext VIEWER = new WorkspaceAccessContext(3L, "viewer", false);
    private static final WorkspaceAccessContext PLATFORM_ADMIN = new WorkspaceAccessContext(99L, "admin", true);

    @jakarta.annotation.Resource
    private WorkspaceTreeService treeService;

    @jakarta.annotation.Resource
    private WorkspaceMemberService memberService;

    @jakarta.annotation.Resource
    private WorkspacePermissionService permissionService;

    @jakarta.annotation.Resource
    private WorkspaceClosureJpaRepository closureRepository;

    @Test
    void createsRootAndChildWithPathClosureAndOwner() {
        var root = createRoot("Acme", "acme", OWNER);
        var child = treeService.createChild(root.id(), createCommand("Engineering", "engineering", OWNER));

        assertThat(root.path()).isEqualTo("acme");
        assertThat(child.path()).isEqualTo("acme/engineering");
        assertThat(child.rootId()).isEqualTo(root.id());
        assertThat(closureRepository.findAncestorIds(child.id())).containsExactly(root.id(), child.id());
        assertThat(memberService.getDirectMembers(root.id(), OWNER))
                .containsExactly(new studio.one.platform.workspace.model.WorkspaceMemberRef(
                        root.id(),
                        OWNER.userId(),
                        WorkspaceRole.OWNER,
                        false));
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

    private studio.one.platform.workspace.model.WorkspaceRef createRoot(
            String name,
            String slug,
            WorkspaceAccessContext actor) {
        return treeService.createRoot(createCommand(name, slug, actor));
    }

    private CreateWorkspaceCommand createCommand(String name, String slug, WorkspaceAccessContext actor) {
        return new CreateWorkspaceCommand(name, slug, WorkspaceVisibility.PRIVATE, actor);
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = WorkspaceClosureEntity.class)
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
                    List.<WorkspacePermissionContributor>of(),
                    settings);
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
                WorkspacePermissionService permissionService) {
            return new DefaultWorkspaceMemberService(
                    workspaceRepository,
                    closureRepository,
                    memberRepository,
                    permissionService);
        }
    }
}
