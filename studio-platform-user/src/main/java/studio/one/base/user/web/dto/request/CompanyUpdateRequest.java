package studio.one.base.user.web.dto.request;

import java.util.Collections;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyUpdateRequest(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 100) String domainName,
        @Size(max = 1000) String description,
        Map<String, String> properties) {

    public CompanyUpdateRequest {
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }
}
