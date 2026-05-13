package studio.one.base.user.web.dto.response;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import studio.one.base.user.domain.model.company.CompanyStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyDto {
    private final Long companyId;
    @NotBlank @Size(max = 100) private final String name;
    @NotBlank @Size(max = 255) private final String displayName;
    @Size(max = 100) private final String domainName;
    @Size(max = 1000) private final String description;
    private final CompanyStatus status;
    private final Instant archivedAt;
    private final Long archivedBy;
    private final Instant creationDate;
    private final Instant modifiedDate;
    private final Map<String, String> properties;

    public CompanyDto(Long companyId, @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 255) String displayName, @Size(max = 100) String domainName, @Size(max = 1000) String description, CompanyStatus status, Instant archivedAt, Long archivedBy, Instant creationDate, Instant modifiedDate, Map<String, String> properties) {
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
        
        this.companyId = companyId;
        this.name = name;
        this.displayName = displayName;
        this.domainName = domainName;
        this.description = description;
        this.status = status;
        this.archivedAt = archivedAt;
        this.archivedBy = archivedBy;
        this.creationDate = creationDate;
        this.modifiedDate = modifiedDate;
        this.properties = properties;
    }

    public Long companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String domainName() {
        return domainName;
    }

    public String description() {
        return description;
    }

    public CompanyStatus status() {
        return status;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public Long archivedBy() {
        return archivedBy;
    }

    public Instant creationDate() {
        return creationDate;
    }

    public Instant modifiedDate() {
        return modifiedDate;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getDescription() {
        return description;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Long getArchivedBy() {
        return archivedBy;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}
