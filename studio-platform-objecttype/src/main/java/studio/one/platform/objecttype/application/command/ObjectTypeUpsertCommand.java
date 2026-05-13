package studio.one.platform.objecttype.application.command;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObjectTypeUpsertCommand)) {
            return false;
        }
        ObjectTypeUpsertCommand that = (ObjectTypeUpsertCommand) o;
        return Objects.equals(objectType, that.objectType)
                && Objects.equals(code, that.code)
                && Objects.equals(name, that.name)
                && Objects.equals(domain, that.domain)
                && Objects.equals(status, that.status)
                && Objects.equals(description, that.description)
                && Objects.equals(updatedBy, that.updatedBy)
                && Objects.equals(updatedById, that.updatedById)
                && Objects.equals(createdBy, that.createdBy)
                && Objects.equals(createdById, that.createdById);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectType, code, name, domain, status, description,
                updatedBy, updatedById, createdBy, createdById);
    }
}
