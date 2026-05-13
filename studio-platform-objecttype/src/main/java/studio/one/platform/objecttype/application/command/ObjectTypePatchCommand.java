package studio.one.platform.objecttype.application.command;

public class ObjectTypePatchCommand {

    private final String code;
    private final String name;
    private final String domain;
    private final String status;
    private final String description;
    private final String updatedBy;
    private final Long updatedById;

    public ObjectTypePatchCommand(String code, String name, String domain, String status, String description, String updatedBy, Long updatedById) {
        this.code = code; this.name = name; this.domain = domain; this.status = status; this.description = description; this.updatedBy = updatedBy; this.updatedById = updatedById;
    }

    public String code() { return code; }
    public String name() { return name; }
    public String domain() { return domain; }
    public String status() { return status; }
    public String description() { return description; }
    public String updatedBy() { return updatedBy; }
    public Long updatedById() { return updatedById; }
}
