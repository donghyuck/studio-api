package studio.one.platform.objecttype.application.command;

import java.util.Objects;

public class ObjectTypePolicyUpsertCommand {

    private final Integer maxFileMb; private final String allowedExt; private final String allowedMime; private final String policyJson; private final String updatedBy; private final Long updatedById; private final String createdBy; private final Long createdById;

    public ObjectTypePolicyUpsertCommand(Integer maxFileMb, String allowedExt, String allowedMime, String policyJson, String updatedBy, Long updatedById, String createdBy, Long createdById) {
        this.maxFileMb = maxFileMb; this.allowedExt = allowedExt; this.allowedMime = allowedMime; this.policyJson = policyJson; this.updatedBy = updatedBy; this.updatedById = updatedById; this.createdBy = createdBy; this.createdById = createdById;
    }

    public Integer maxFileMb() { return maxFileMb; }
    public String allowedExt() { return allowedExt; }
    public String allowedMime() { return allowedMime; }
    public String policyJson() { return policyJson; }
    public String updatedBy() { return updatedBy; }
    public Long updatedById() { return updatedById; }
    public String createdBy() { return createdBy; }
    public Long createdById() { return createdById; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObjectTypePolicyUpsertCommand)) {
            return false;
        }
        ObjectTypePolicyUpsertCommand that = (ObjectTypePolicyUpsertCommand) o;
        return Objects.equals(maxFileMb, that.maxFileMb)
                && Objects.equals(allowedExt, that.allowedExt)
                && Objects.equals(allowedMime, that.allowedMime)
                && Objects.equals(policyJson, that.policyJson)
                && Objects.equals(updatedBy, that.updatedBy)
                && Objects.equals(updatedById, that.updatedById)
                && Objects.equals(createdBy, that.createdBy)
                && Objects.equals(createdById, that.createdById);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxFileMb, allowedExt, allowedMime, policyJson,
                updatedBy, updatedById, createdBy, createdById);
    }
}
