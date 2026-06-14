package studio.one.platform.markdown.domain;

public enum MarkdownRevisionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
