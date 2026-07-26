package studio.one.platform.documentmetadata;

public enum DocumentSemanticTypeSelection {
    AUTO,
    GENERAL,
    BOOK,
    ACADEMIC_PAPER,
    THESIS,
    REPORT,
    POLICY,
    MANUAL,
    PRESENTATION;

    public DocumentSemanticType explicitType() {
        return this == AUTO ? null : DocumentSemanticType.valueOf(name());
    }
}
