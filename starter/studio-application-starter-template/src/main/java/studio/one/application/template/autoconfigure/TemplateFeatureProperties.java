package studio.one.application.template.autoconfigure;

import jakarta.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.WebEndpointProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".template")
@Getter
@Setter
@Validated
@EqualsAndHashCode(callSuper = true)
public class TemplateFeatureProperties extends FeatureToggle {

    @Valid
    private WebEndpointProperties web = new WebEndpointProperties();

    @Override
    public studio.one.platform.autoconfigure.PersistenceProperties.Type resolvePersistence(
            studio.one.platform.autoconfigure.PersistenceProperties.Type globalDefault) {
        if (getPersistence() != null) {
            return getPersistence();
        }
        if (globalDefault == studio.one.platform.autoconfigure.PersistenceProperties.Type.mybatis) {
            return studio.one.platform.autoconfigure.PersistenceProperties.Type.jdbc;
        }
        return super.resolvePersistence(globalDefault);
    }
 
}
