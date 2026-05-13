package studio.one.platform.objecttype.web.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class ObjectTypePatchRequest {

    @Pattern(regexp = "^[a-z][a-z0-9_\\-]{1,79}$") private String code;
    private String name; private String domain; private String status; private String description;
    @NotBlank private String updatedBy;
    @NotNull @Min(1) private Long updatedById;

    public ObjectTypePatchRequest() { }
    public ObjectTypePatchRequest(String code, String name, String domain, String status, String description, String updatedBy, Long updatedById) { this.code = code; this.name = name; this.domain = domain; this.status = status; this.description = description; this.updatedBy = updatedBy; this.updatedById = updatedById; }

    public String code() { return code; } public String name() { return name; } public String domain() { return domain; } public String status() { return status; } public String description() { return description; } public String updatedBy() { return updatedBy; } public Long updatedById() { return updatedById; }
    public String getCode() { return code; } public String getName() { return name; } public String getDomain() { return domain; } public String getStatus() { return status; } public String getDescription() { return description; } public String getUpdatedBy() { return updatedBy; } public Long getUpdatedById() { return updatedById; }
    public void setCode(String code) { this.code = code; } public void setName(String name) { this.name = name; } public void setDomain(String domain) { this.domain = domain; } public void setStatus(String status) { this.status = status; } public void setDescription(String description) { this.description = description; } public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; } public void setUpdatedById(Long updatedById) { this.updatedById = updatedById; }
}
