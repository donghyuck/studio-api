package studio.one.platform.workspace.service;

import java.util.List;

import studio.one.platform.workspace.model.WorkspaceMemberRef;

public interface WorkspaceMemberService {

    WorkspaceMemberRef addMember(Long workspaceId, WorkspaceMemberCommand command);

    WorkspaceMemberRef changeRole(Long workspaceId, WorkspaceMemberCommand command);

    void removeMember(Long workspaceId, Long userId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getDirectMembers(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getEffectiveMembers(Long workspaceId, WorkspaceAccessContext actor);
}
