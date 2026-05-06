package studio.one.base.user.company.model;

public enum CompanyRole {
    MEMBER(1),
    BILLING_ADMIN(2),
    ADMIN(3),
    OWNER(4);

    private final int rank;

    CompanyRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
