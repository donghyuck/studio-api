package studio.one.platform.objecttype.web.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ObjectTypePolicyUpsertRequest {

    private Integer maxFileMb; private String allowedExt; private String allowedMime; private String policyJson;
    @NotBlank private String updatedBy; @NotNull @Min(1) private Long updatedById; @NotBlank private String createdBy; @NotNull @Min(1) private Long createdById;

    public ObjectTypePolicyUpsertRequest() { }
    public ObjectTypePolicyUpsertRequest(Integer maxFileMb, String allowedExt, String allowedMime, String policyJson, String updatedBy, Long updatedById, String createdBy, Long createdById) { this.maxFileMb = maxFileMb; this.allowedExt = allowedExt; this.allowedMime = allowedMime; this.policyJson = policyJson; this.updatedBy = updatedBy; this.updatedById = updatedById; this.createdBy = createdBy; this.createdById = createdById; }

    public Integer maxFileMb() { return maxFileMb; } public String allowedExt() { return allowedExt; } public String allowedMime() { return allowedMime; } public String policyJson() { return policyJson; } public String updatedBy() { return updatedBy; } public Long updatedById() { return updatedById; } public String createdBy() { return createdBy; } public Long createdById() { return createdById; }
    public Integer getMaxFileMb() { return maxFileMb; } public String getAllowedExt() { return allowedExt; } public String getAllowedMime() { return allowedMime; } public String getPolicyJson() { return policyJson; } public String getUpdatedBy() { return updatedBy; } public Long getUpdatedById() { return updatedById; } public String getCreatedBy() { return createdBy; } public Long getCreatedById() { return createdById; }
    public void setMaxFileMb(Integer maxFileMb) { this.maxFileMb = maxFileMb; } public void setAllowedExt(String allowedExt) { this.allowedExt = allowedExt; } public void setAllowedMime(String allowedMime) { this.allowedMime = allowedMime; } public void setPolicyJson(String policyJson) { this.policyJson = policyJson; } public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; } public void setUpdatedById(Long updatedById) { this.updatedById = updatedById; } public void setCreatedBy(String createdBy) { this.createdBy = createdBy; } public void setCreatedById(Long createdById) { this.createdById = createdById; }
}
