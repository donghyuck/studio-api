package studio.one.platform.objecttype.application.result;

import java.time.OffsetDateTime;

public class ObjectTypeView {

    private final int objectType; private final String code; private final String name; private final String domain; private final String status; private final String description; private final String createdBy; private final long createdById; private final OffsetDateTime createdAt; private final String updatedBy; private final long updatedById; private final OffsetDateTime updatedAt;

    public ObjectTypeView(int objectType, String code, String name, String domain, String status, String description, String createdBy, long createdById, OffsetDateTime createdAt, String updatedBy, long updatedById, OffsetDateTime updatedAt) {
        this.objectType = objectType; this.code = code; this.name = name; this.domain = domain; this.status = status; this.description = description; this.createdBy = createdBy; this.createdById = createdById; this.createdAt = createdAt; this.updatedBy = updatedBy; this.updatedById = updatedById; this.updatedAt = updatedAt;
    }

    public int objectType() { return objectType; }
    public String code() { return code; }
    public String name() { return name; }
    public String domain() { return domain; }
    public String status() { return status; }
    public String description() { return description; }
    public String createdBy() { return createdBy; }
    public long createdById() { return createdById; }
    public OffsetDateTime createdAt() { return createdAt; }
    public String updatedBy() { return updatedBy; }
    public long updatedById() { return updatedById; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
