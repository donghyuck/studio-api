package studio.one.platform.workspace.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.command.WorkspaceMemberCommand;
import studio.one.platform.workspace.application.command.WorkspaceMemberListQuery;
import studio.one.platform.workspace.domain.model.WorkspaceMemberRef;

public interface WorkspaceMemberService {

    WorkspaceMemberRef addMember(Long workspaceId, WorkspaceMemberCommand command);

    WorkspaceMemberRef changeRole(Long workspaceId, WorkspaceMemberCommand command);

    void removeMember(Long workspaceId, Long userId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getDirectMembers(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getEffectiveMembers(Long workspaceId, WorkspaceAccessContext actor);

    Page<WorkspaceMemberRef> getDirectMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor);

    Page<WorkspaceMemberRef> getEffectiveMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor);
}
