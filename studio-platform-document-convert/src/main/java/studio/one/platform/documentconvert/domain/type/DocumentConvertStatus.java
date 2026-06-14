package studio.one.platform.documentconvert.domain.type;

public enum DocumentConvertStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
