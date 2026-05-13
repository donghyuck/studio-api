package studio.one.platform.objecttype.application.command;

public class ObjectTypeUpsertCommand {

    private final Integer objectType; private final String code; private final String name; private final String domain; private final String status; private final String description; private final String updatedBy; private final Long updatedById; private final String createdBy; private final Long createdById;

    public ObjectTypeUpsertCommand(Integer objectType, String code, String name, String domain, String status, String description, String updatedBy, Long updatedById, String createdBy, Long createdById) {
        this.objectType = objectType; this.code = code; this.name = name; this.domain = domain; this.status = status; this.description = description; this.updatedBy = updatedBy; this.updatedById = updatedById; this.createdBy = createdBy; this.createdById = createdById;
    }

    public Integer objectType() { return objectType; }
    public String code() { return code; }
    public String name() { return name; }
    public String domain() { return domain; }
    public String status() { return status; }
    public String description() { return description; }
    public String updatedBy() { return updatedBy; }
    public Long updatedById() { return updatedById; }
    public String createdBy() { return createdBy; }
    public Long createdById() { return createdById; }
}
