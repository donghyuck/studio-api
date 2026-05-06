package studio.one.platform.workspace.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.workspace.model.WorkspaceMemberRef;

public interface WorkspaceMemberService {

    WorkspaceMemberRef addMember(Long workspaceId, WorkspaceMemberCommand command);

    WorkspaceMemberRef changeRole(Long workspaceId, WorkspaceMemberCommand command);

    void removeMember(Long workspaceId, Long userId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getDirectMembers(Long workspaceId, WorkspaceAccessContext actor);

    List<WorkspaceMemberRef> getEffectiveMembers(Long workspaceId, WorkspaceAccessContext actor);

    default Page<WorkspaceMemberRef> getDirectMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor) {
        return page(getDirectMembers(workspaceId, actor), pageable);
    }

    default Page<WorkspaceMemberRef> getEffectiveMembers(
            Long workspaceId,
            WorkspaceMemberListQuery query,
            Pageable pageable,
            WorkspaceAccessContext actor) {
        return page(getEffectiveMembers(workspaceId, actor), pageable);
    }

    private static Page<WorkspaceMemberRef> page(List<WorkspaceMemberRef> members, Pageable pageable) {
        Pageable resolved = pageable == null ? Pageable.unpaged() : pageable;
        if (resolved.isUnpaged()) {
            return new PageImpl<>(members);
        }
        int start = Math.toIntExact(Math.min(resolved.getOffset(), members.size()));
        int end = Math.min(start + resolved.getPageSize(), members.size());
        return new PageImpl<>(members.subList(start, end), resolved, members.size());
    }
}
