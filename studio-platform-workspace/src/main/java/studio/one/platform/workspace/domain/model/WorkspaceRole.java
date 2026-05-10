package studio.one.platform.workspace.domain.model;

public enum WorkspaceRole {
    VIEWER(1),
    EDITOR(2),
    ADMIN(3),
    OWNER(4);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(WorkspaceRole other) {
        return other != null && rank >= other.rank;
    }

    public static WorkspaceRole strongest(WorkspaceRole first, WorkspaceRole second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.rank >= second.rank ? first : second;
    }
}
