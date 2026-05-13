package studio.one.platform.objecttype.application.command;

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
}
