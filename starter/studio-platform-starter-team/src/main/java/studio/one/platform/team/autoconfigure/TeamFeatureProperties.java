package studio.one.platform.team.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.WebEndpointProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".team")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class TeamFeatureProperties extends FeatureToggle {
    private Web web = new Web();

    @Getter
    @Setter
    public static class Web extends WebEndpointProperties {
        private String publicBasePath = "/api/teams";
        private String mgmtMigrationBasePath = "/api/mgmt/team-migrations";
    }
}
