package studio.one.platform.autoconfigure.objecttype;

import javax.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.WebEndpointProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".objecttype")
@Getter
@Setter
@Validated
@EqualsAndHashCode(callSuper = true)
public class ObjectTypeFeatureProperties extends FeatureToggle {

    @Valid
    private WebEndpointProperties web = new WebEndpointProperties();
}
