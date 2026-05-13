package studio.one.application.web.service;

public class AttachmentRagIndexResult {
    private final AttachmentRagIndexDiagnostics diagnostics;

    public AttachmentRagIndexResult(AttachmentRagIndexDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public AttachmentRagIndexDiagnostics diagnostics() {
        return diagnostics;
    }
}
