package studio.one.platform.workspace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.workspace.application.error.WorkspaceConflictException;
import studio.one.platform.workspace.application.error.WorkspaceValidationException;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberEntity;
import studio.one.platform.workspace.infrastructure.persistence.jpa.WorkspaceMemberJpaRepository;

@DataJpaTest
@ContextConfiguration(classes = DefaultTeamMigrationWorkspaceAdapterTest.Config.class)
class DefaultTeamMigrationWorkspaceAdapterTest {

    @Resource
    TeamMigrationWorkspacePort adapter;

    @Resource
    WorkspaceJpaRepository workspaceRepository;

    @Resource
    WorkspaceClosureJpaRepository closureRepository;

    @Resource
    WorkspaceMemberJpaRepository memberRepository;

    private WorkspaceEntity root;
    private WorkspaceEntity child;

    @BeforeEach
    void setUp() {
        root = workspace("legacy", "legacy", null, null, 0);
        root.setRootId(root.getWorkspaceId());
        root = workspaceRepository.saveAndFlush(root);
        child = workspace("docs", "legacy/docs", root.getWorkspaceId(), root.getWorkspaceId(), 1);
        closureRepository.save(new WorkspaceClosureEntity(root.getWorkspaceId(), root.getWorkspaceId(), 0));
        closureRepository.save(new WorkspaceClosureEntity(root.getWorkspaceId(), child.getWorkspaceId(), 1));
        closureRepository.save(new WorkspaceClosureEntity(child.getWorkspaceId(), child.getWorkspaceId(), 0));
        member(root.getWorkspaceId(), 1L, WorkspaceRole.OWNER);
        member(child.getWorkspaceId(), 2L, WorkspaceRole.VIEWER);
    }

    @Test
    void dryRunCapturesMultipleRootForestAndDirectMemberCount() {
        WorkspaceEntity secondRoot = root("second", 20L);
        member(secondRoot.getWorkspaceId(), 3L, WorkspaceRole.VIEWER);

        TeamMigrationWorkspacePort.WorkspaceSnapshot snapshot =
                adapter.captureBeforeSnapshot(List.of(secondRoot.getWorkspaceId(), root.getWorkspaceId()));

        assertThat(snapshot.reference()).startsWith("workspace-v2.");
        assertThat(snapshot.workspaceCount()).isEqualTo(3);
        assertThat(snapshot.memberCount()).isEqualTo(3);
        assertThatThrownBy(() -> adapter.captureBeforeSnapshot(List.of(root.getWorkspaceId(), root.getWorkspaceId())))
                .isInstanceOf(WorkspaceValidationException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void applyAndVerifyAreExactAndApplyIsIdempotent() {
        var snapshot = adapter.captureBeforeSnapshot(List.of(root.getWorkspaceId()));
        List<Long> beforeIds = List.of(root.getWorkspaceId(), child.getWorkspaceId());

        adapter.assignToTeam("run-1", 100L, List.of(root.getWorkspaceId()), snapshot.reference());
        adapter.assignToTeam("run-1", 100L, List.of(root.getWorkspaceId()), snapshot.reference());

        assertThat(workspaceRepository.findByWorkspaceIdIn(beforeIds))
                .extracting(WorkspaceEntity::getTeamId)
                .containsOnly(100L);
        assertThat(workspaceRepository.findByWorkspaceIdIn(beforeIds))
                .extracting(WorkspaceEntity::getCompanyId)
                .containsOnlyNulls();
        assertThat(workspaceRepository.findById(child.getWorkspaceId()).orElseThrow())
                .satisfies(current -> {
                    assertThat(current.getParentId()).isEqualTo(root.getWorkspaceId());
                    assertThat(current.getRootId()).isEqualTo(root.getWorkspaceId());
                    assertThat(current.getPath()).isEqualTo("legacy/docs");
                });
        assertThat(adapter.verifyAssignment(
                "run-1", 100L, List.of(root.getWorkspaceId()), snapshot.reference()).matches()).isTrue();
    }

    @Test
    void rollbackRestoresPriorTeamAndStructureAndIsIdempotent() {
        root.setTeamId(50L);
        child.setTeamId(50L);
        workspaceRepository.saveAllAndFlush(List.of(root, child));
        var snapshot = adapter.captureBeforeSnapshot(List.of(root.getWorkspaceId()));

        adapter.assignToTeam("run-2", 100L, List.of(root.getWorkspaceId()), snapshot.reference());
        adapter.rollbackAssignment("run-2", 100L, List.of(root.getWorkspaceId()), snapshot.reference());
        adapter.rollbackAssignment("run-2", 100L, List.of(root.getWorkspaceId()), snapshot.reference());

        assertThat(workspaceRepository.findByWorkspaceIdIn(List.of(root.getWorkspaceId(), child.getWorkspaceId())))
                .extracting(WorkspaceEntity::getTeamId)
                .containsOnly(50L);
        assertThat(workspaceRepository.findByWorkspaceIdIn(List.of(root.getWorkspaceId(), child.getWorkspaceId())))
                .extracting(WorkspaceEntity::getCompanyId)
                .containsOnly(10L);
        assertThat(workspaceRepository.findById(child.getWorkspaceId()).orElseThrow().getPath())
                .isEqualTo("legacy/docs");
    }

    @Test
    void tamperedSnapshotAndChangedStructureFailClosed() {
        var snapshot = adapter.captureBeforeSnapshot(List.of(root.getWorkspaceId()));
        char last = snapshot.reference().charAt(snapshot.reference().length() - 1);
        String tampered = snapshot.reference().substring(0, snapshot.reference().length() - 1)
                + (last == '0' ? '1' : '0');

        assertThatThrownBy(() -> adapter.assignToTeam(
                "run-3", 100L, List.of(root.getWorkspaceId()), tampered))
                .isInstanceOf(WorkspaceValidationException.class);

        child.setPath("legacy/changed");
        workspaceRepository.saveAndFlush(child);
        assertThatThrownBy(() -> adapter.assignToTeam(
                "run-3", 100L, List.of(root.getWorkspaceId()), snapshot.reference()))
                .isInstanceOf(WorkspaceConflictException.class)
                .hasMessageContaining("structure changed");
        assertThat(adapter.verifyAssignment(
                "run-3", 100L, List.of(root.getWorkspaceId()), snapshot.reference()).matches()).isFalse();
    }

    @Test
    void applySupportsMultipleIndependentRootsForOneTeam() {
        WorkspaceEntity occupied = workspace("occupied", "occupied", null, null, 0);
        occupied.setRootId(occupied.getWorkspaceId());
        occupied = workspaceRepository.saveAndFlush(occupied);
        occupied.setRootId(occupied.getWorkspaceId());
        workspaceRepository.saveAndFlush(occupied);
        closureRepository.save(new WorkspaceClosureEntity(
                occupied.getWorkspaceId(), occupied.getWorkspaceId(), 0));
        var roots = List.of(root.getWorkspaceId(), occupied.getWorkspaceId());
        var snapshot = adapter.captureBeforeSnapshot(roots);

        adapter.assignToTeam("run-4", 100L, roots, snapshot.reference());

        assertThat(workspaceRepository.findByTeamIdAndParentIdIsNullOrderByPositionAscWorkspaceIdAsc(100L))
                .extracting(WorkspaceEntity::getWorkspaceId)
                .containsExactlyInAnyOrder(root.getWorkspaceId(), occupied.getWorkspaceId());
        assertThat(adapter.verifyAssignment("run-4", 100L, roots, snapshot.reference()).matches()).isTrue();
    }

    private WorkspaceEntity root(String slug, Long companyId) {
        WorkspaceEntity entity = workspace(slug, slug, null, null, 0);
        entity.setCompanyId(companyId);
        entity.setRootId(entity.getWorkspaceId());
        entity = workspaceRepository.saveAndFlush(entity);
        closureRepository.save(new WorkspaceClosureEntity(
                entity.getWorkspaceId(), entity.getWorkspaceId(), 0));
        return entity;
    }

    private WorkspaceEntity workspace(String slug, String path, Long parentId, Long rootId, int depth) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setCompanyId(10L);
        workspace.setParentId(parentId);
        workspace.setRootId(rootId);
        workspace.setName(slug);
        workspace.setSlug(slug);
        workspace.setPath(path);
        workspace.setDepth(depth);
        workspace.setPosition(0);
        workspace.setVisibility(WorkspaceVisibility.PRIVATE);
        workspace.setCreatedBy(1L);
        workspace.setUpdatedBy(1L);
        return workspaceRepository.saveAndFlush(workspace);
    }

    private void member(Long workspaceId, Long userId, WorkspaceRole role) {
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        member.setCreatedBy(1L);
        memberRepository.save(member);
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = WorkspaceEntity.class)
    @EnableJpaRepositories(basePackageClasses = {
            WorkspaceJpaRepository.class,
            WorkspaceClosureJpaRepository.class,
            WorkspaceMemberJpaRepository.class })
    static class Config {
        @Bean
        TeamMigrationWorkspacePort teamMigrationWorkspacePort(
                WorkspaceJpaRepository workspaceRepository,
                WorkspaceClosureJpaRepository closureRepository,
                WorkspaceMemberJpaRepository memberRepository) {
            return new DefaultTeamMigrationWorkspaceAdapter(
                    workspaceRepository,
                    closureRepository,
                    memberRepository);
        }
    }
}
