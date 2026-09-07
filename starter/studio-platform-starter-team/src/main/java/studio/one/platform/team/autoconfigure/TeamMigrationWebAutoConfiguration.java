package studio.one.platform.team.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.web.controller.TeamMigrationMgmtController;

@AutoConfiguration(after = TeamMigrationAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team.web", name = "enabled", havingValue = "true")
@ConditionalOnBean(TeamMigrationService.class)
public class TeamMigrationWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TeamMigrationMgmtController teamMigrationMgmtController(
            TeamMigrationService migrationService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new TeamMigrationMgmtController(migrationService, principalResolverProvider);
    }
}
