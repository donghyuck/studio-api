package studio.one.platform.objecttype.application.result;

import java.time.OffsetDateTime;

public class ObjectTypePolicyView {

    private final int objectType; private final Integer maxFileMb; private final String allowedExt; private final String allowedMime; private final String policyJson; private final String createdBy; private final long createdById; private final OffsetDateTime createdAt; private final String updatedBy; private final long updatedById; private final OffsetDateTime updatedAt;

    public ObjectTypePolicyView(int objectType, Integer maxFileMb, String allowedExt, String allowedMime, String policyJson, String createdBy, long createdById, OffsetDateTime createdAt, String updatedBy, long updatedById, OffsetDateTime updatedAt) {
        this.objectType = objectType; this.maxFileMb = maxFileMb; this.allowedExt = allowedExt; this.allowedMime = allowedMime; this.policyJson = policyJson; this.createdBy = createdBy; this.createdById = createdById; this.createdAt = createdAt; this.updatedBy = updatedBy; this.updatedById = updatedById; this.updatedAt = updatedAt;
    }

    public int objectType() { return objectType; }
    public Integer maxFileMb() { return maxFileMb; }
    public String allowedExt() { return allowedExt; }
    public String allowedMime() { return allowedMime; }
    public String policyJson() { return policyJson; }
    public String createdBy() { return createdBy; }
    public long createdById() { return createdById; }
    public OffsetDateTime createdAt() { return createdAt; }
    public String updatedBy() { return updatedBy; }
    public long updatedById() { return updatedById; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
