package studio.one.application.webknowledge.domain;

public enum WebKnowledgeStatus {
    PENDING,
    FETCHING,
    NORMALIZING,
    INDEXING,
    COMPLETED,
    UNCHANGED,
    FAILED,
    CANCELLED
}
