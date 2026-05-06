package studio.one.base.user.web.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import studio.one.base.user.company.model.CompanyStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyDto(
        Long companyId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 100) String domainName,
        @Size(max = 1000) String description,
        CompanyStatus status,
        Instant archivedAt,
        Long archivedBy,
        Instant creationDate,
        Instant modifiedDate,
        Map<String, String> properties) {

    public CompanyDto {
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }
}
