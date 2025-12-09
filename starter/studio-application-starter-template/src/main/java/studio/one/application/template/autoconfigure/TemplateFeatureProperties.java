package studio.one.application.template.autoconfigure;

import javax.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.SimpleWebProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".template")
@Getter
@Setter
@Validated
@EqualsAndHashCode(callSuper = true)
public class TemplateFeatureProperties extends FeatureToggle {

    @Valid
    private SimpleWebProperties web = new SimpleWebProperties();
 
}
