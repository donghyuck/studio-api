package studio.echo.platform.starter.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties(prefix = "studio.features")
public class FeaturesProperties {

    private FeatureToggle applicationProperties;
    private FeatureToggle imageService;
    private FeatureToggle fileUpload;

    @Data
    public static class FeatureToggle {
        private boolean enabled = false;
    }
}
