package studio.one.platform.objecttype.web.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class ObjectTypeUpsertRequest {

    @Min(1) private Integer objectType;
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_\\-]{1,79}$") private String code;
    @NotBlank private String name; @NotBlank private String domain; @NotBlank private String status;
    private String description; @NotBlank private String updatedBy; @NotNull @Min(1) private Long updatedById; @NotBlank private String createdBy; @NotNull @Min(1) private Long createdById;

    public ObjectTypeUpsertRequest() { }
    public ObjectTypeUpsertRequest(Integer objectType, String code, String name, String domain, String status, String description, String updatedBy, Long updatedById, String createdBy, Long createdById) { this.objectType = objectType; this.code = code; this.name = name; this.domain = domain; this.status = status; this.description = description; this.updatedBy = updatedBy; this.updatedById = updatedById; this.createdBy = createdBy; this.createdById = createdById; }

    public Integer objectType() { return objectType; } public String code() { return code; } public String name() { return name; } public String domain() { return domain; } public String status() { return status; } public String description() { return description; } public String updatedBy() { return updatedBy; } public Long updatedById() { return updatedById; } public String createdBy() { return createdBy; } public Long createdById() { return createdById; }
    public Integer getObjectType() { return objectType; } public String getCode() { return code; } public String getName() { return name; } public String getDomain() { return domain; } public String getStatus() { return status; } public String getDescription() { return description; } public String getUpdatedBy() { return updatedBy; } public Long getUpdatedById() { return updatedById; } public String getCreatedBy() { return createdBy; } public Long getCreatedById() { return createdById; }
    public void setObjectType(Integer objectType) { this.objectType = objectType; } public void setCode(String code) { this.code = code; } public void setName(String name) { this.name = name; } public void setDomain(String domain) { this.domain = domain; } public void setStatus(String status) { this.status = status; } public void setDescription(String description) { this.description = description; } public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; } public void setUpdatedById(Long updatedById) { this.updatedById = updatedById; } public void setCreatedBy(String createdBy) { this.createdBy = createdBy; } public void setCreatedById(Long createdById) { this.createdById = createdById; }
}
