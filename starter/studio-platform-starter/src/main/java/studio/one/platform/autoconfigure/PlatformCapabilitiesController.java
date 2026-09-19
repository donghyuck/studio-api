package studio.one.platform.autoconfigure;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import studio.one.platform.web.dto.ApiResponse;

@RestController
public class PlatformCapabilitiesController {
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final Map<String, String> descriptors;

    PlatformCapabilitiesController(ObjectProvider<RequestMappingHandlerMapping> mappings,
            Map<String, String> descriptors) {
        this.mappings = mappings;
        this.descriptors = descriptors;
    }

    @GetMapping("${studio.admin.capabilities-path:/api/platform/capabilities}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Capabilities> capabilities() {
        RequestMappingHandlerMapping mapping = mappings.getIfAvailable();
        Set<String> controllers = mapping == null ? Set.of() : mapping.getHandlerMethods().values().stream()
                .map(handler -> handler.getBeanType().getName()).collect(Collectors.toSet());
        Map<String, Boolean> features = new java.util.TreeMap<>();
        descriptors.forEach((key, type) -> features.put(key, controllers.contains(type)));
        return ApiResponse.ok(new Capabilities("1", Map.copyOf(features)));
    }

    public record Capabilities(String contractVersion, Map<String, Boolean> features) { }

}
