package studio.one.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.ApplicationProperties.PREFIX )
public class PropertiesProperties extends FeatureToggle {

}
