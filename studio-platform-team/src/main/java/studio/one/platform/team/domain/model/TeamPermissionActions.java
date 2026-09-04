package studio.one.platform.team.domain.model;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TeamPermissionActions {

    public static final String READ = "team.read";
    public static final String UPDATE = "team.update";
    public static final String ARCHIVE = "team.archive";
    public static final String MEMBER_READ = "team.member.read";
    public static final String MEMBER_MANAGE = "team.member.manage";
    public static final String WORKSPACE_READ = "team.workspace.read";
    public static final String WORKSPACE_CREATE = "team.workspace.create";
    public static final String KNOWLEDGE_READ = "team.knowledge.read";
    public static final String CHAT = "team.chat";

    private static final Set<String> MEMBER_ACTIONS = Set.of(
            READ, MEMBER_READ, WORKSPACE_READ, KNOWLEDGE_READ, CHAT);
    private static final Set<String> ADMIN_ACTIONS = Set.of(
            UPDATE, MEMBER_MANAGE, WORKSPACE_CREATE);

    private TeamPermissionActions() {
    }

    public static Set<String> grantedActions(TeamRole role) {
        if (role == null) {
            return Set.of();
        }
        Set<String> actions = new LinkedHashSet<>(MEMBER_ACTIONS);
        if (role.rank() >= TeamRole.ADMIN.rank()) {
            actions.addAll(ADMIN_ACTIONS);
        }
        if (role == TeamRole.OWNER) {
            actions.add(ARCHIVE);
        }
        return Set.copyOf(actions);
    }
}
