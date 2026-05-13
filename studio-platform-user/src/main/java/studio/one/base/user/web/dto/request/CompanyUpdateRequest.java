package studio.one.base.user.web.dto.request;

import java.util.Collections;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CompanyUpdateRequest {
    @NotBlank @Size(max = 255) private final String displayName;
    @Size(max = 100) private final String domainName;
    @Size(max = 1000) private final String description;
    private final Map<String, String> properties;

    public CompanyUpdateRequest(@NotBlank @Size(max = 255) String displayName, @Size(max = 100) String domainName, @Size(max = 1000) String description, Map<String, String> properties) {
        properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
        
        this.displayName = displayName;
        this.domainName = domainName;
        this.description = description;
        this.properties = properties;
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

    public Map<String, String> properties() {
        return properties;
    }
}
