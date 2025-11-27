package studio.one.platform.autoconfigure.features.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.ApplicationProperties.PREFIX )
@Getter @Setter
public class PropertiesProperties extends FeatureToggle {

}
