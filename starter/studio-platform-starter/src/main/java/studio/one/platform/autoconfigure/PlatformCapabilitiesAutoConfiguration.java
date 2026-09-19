package studio.one.platform.autoconfigure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Reads module-owned descriptors and reports only registered web capabilities. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "org.springframework.security.access.prepost.PreAuthorize"})
public class PlatformCapabilitiesAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PlatformCapabilitiesController platformCapabilitiesController(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings)
            throws IOException {
        Map<String, String> descriptors = new LinkedHashMap<>();
        for (var resource : new PathMatchingResourcePatternResolver().getResources(
                "classpath*:META-INF/studio/features.properties")) {
            Properties properties = new Properties();
            try (var input = resource.getInputStream()) {
                properties.load(input);
            }
            for (String key : properties.stringPropertyNames()) {
                String type = properties.getProperty(key).trim();
                String previous = descriptors.putIfAbsent(key, type);
                if (previous != null && !previous.equals(type)) {
                    throw new IllegalStateException("Duplicate Studio feature descriptor: " + key);
                }
            }
        }
        return new PlatformCapabilitiesController(mappings, Map.copyOf(descriptors));
    }

}
