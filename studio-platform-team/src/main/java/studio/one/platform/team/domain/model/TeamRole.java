package studio.one.platform.team.domain.model;

public enum TeamRole {
    MEMBER(1),
    ADMIN(2),
    OWNER(3);

    private final int rank;

    TeamRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
