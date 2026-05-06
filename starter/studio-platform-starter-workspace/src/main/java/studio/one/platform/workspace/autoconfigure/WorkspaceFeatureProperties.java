package studio.one.platform.workspace.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.autoconfigure.WebEndpointProperties;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".workspace")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Validated
public class WorkspaceFeatureProperties extends FeatureToggle {

    private Web web = new Web();
    private boolean companyRequired = false;

    @Getter
    @Setter
    public static class Web extends WebEndpointProperties {
        private String publicBasePath = "/api/workspaces";

        public Web() {
            setMgmtBasePath("/api/mgmt/workspaces");
        }
    }
}
