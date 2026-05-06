package studio.one.application.wiki.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import studio.one.application.wiki.permission.WikiWorkspacePermissionContributor;
import studio.one.platform.constant.PropertyKeys;

@AutoConfiguration(beforeName = "studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration")
@EnableConfigurationProperties(WikiFeatureProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".wiki", name = "enabled", havingValue = "true")
public class WikiPermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WikiWorkspacePermissionContributor wikiWorkspacePermissionContributor() {
        return new WikiWorkspacePermissionContributor();
    }
}
