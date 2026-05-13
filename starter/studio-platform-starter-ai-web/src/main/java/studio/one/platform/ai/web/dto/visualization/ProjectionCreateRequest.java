package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ProjectionCreateRequest {

    @NotBlank
    @Size(max = 200)
    private final String name;

    private final List<String> targetTypes;

    private final String algorithm;

    private final Map<String, Object> filters;

    @JsonCreator
    public ProjectionCreateRequest(
            @JsonProperty("name") String name,
            @JsonProperty("targetTypes") List<String> targetTypes,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("filters") Map<String, Object> filters
    ) {
        this.name = name;
        this.targetTypes = targetTypes;
        this.algorithm = algorithm;
        this.filters = filters;
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public List<String> getTargetTypes() {
        return targetTypes;
    }

    public String algorithm() {
        return algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

}