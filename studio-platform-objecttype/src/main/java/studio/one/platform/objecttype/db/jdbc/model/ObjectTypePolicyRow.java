package studio.one.platform.objecttype.db.jdbc.model;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectTypePolicyRow {
    private int objectType;
    private Integer maxFileMb;
    private String allowedExt;
    private String allowedMime;
    private String policyJson;
    private String createdBy;
    private long createdById;
    private Instant createdAt;
    private String updatedBy;
    private long updatedById;
    private Instant updatedAt;
}
