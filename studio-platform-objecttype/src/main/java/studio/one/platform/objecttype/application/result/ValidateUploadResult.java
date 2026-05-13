package studio.one.platform.objecttype.application.result;

public class ValidateUploadResult {

    private final boolean allowed;
    private final String reason;

    public ValidateUploadResult(boolean allowed, String reason) {
        this.allowed = allowed; this.reason = reason;
    }

    public boolean allowed() { return allowed; }
    public String reason() { return reason; }
}
