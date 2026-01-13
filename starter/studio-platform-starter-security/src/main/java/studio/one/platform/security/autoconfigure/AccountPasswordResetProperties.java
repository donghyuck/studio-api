package studio.one.platform.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.WebEndpointProperties;
import studio.one.platform.constant.PropertyKeys;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ConfigurationProperties(prefix = PropertyKeys.Security.Auth.PASSWORD_RESET)
public class AccountPasswordResetProperties extends FeatureToggle {

    private String resetPasswordUrl;
    private WebEndpointProperties web = new WebEndpointProperties();

    public PersistenceProperties.Type resolvePersistence(PersistenceProperties.Type globalDefault) {
        if (getPersistence() != null) {
            return getPersistence();
        }
        return globalDefault != null ? globalDefault : PersistenceProperties.Type.jpa;
    }


}
