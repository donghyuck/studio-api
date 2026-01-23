package studio.one.platform.objecttype.db.jdbc.model;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectTypeRow {
    private int objectType;
    private String code;
    private String name;
    private String domain;
    private String status;
    private String description;
    private String createdBy;
    private long createdById;
    private Instant createdAt;
    private String updatedBy;
    private long updatedById;
    private Instant updatedAt;
}
