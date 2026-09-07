package studio.one.platform.team.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.team.application.service.DefaultTeamMigrationService;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunJpaRepository;

/** Wires migration only after Team core and its cross-module adapters are available. */
@AutoConfiguration(after = TeamAutoConfiguration.class)
@AutoConfigureAfter(name = {
        "studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration",
        "studio.one.platform.ai.autoconfigure.AiWebAutoConfiguration" })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team", name = "enabled", havingValue = "true")
public class TeamMigrationAutoConfiguration {

    @Bean
    @ConditionalOnBean({
            TeamMigrationRunJpaRepository.class,
            TeamMigrationLockJpaRepository.class,
            TeamService.class,
            TeamAuthorizationPort.class,
            TeamMigrationWorkspacePort.class,
            TeamMigrationKnowledgePort.class })
    @ConditionalOnMissingBean
    TeamMigrationService teamMigrationService(
            TeamMigrationRunJpaRepository runRepository,
            TeamMigrationLockJpaRepository lockRepository,
            TeamService teamService,
            TeamAuthorizationPort authorizationPort,
            TeamMigrationWorkspacePort workspacePort,
            TeamMigrationKnowledgePort knowledgePort) {
        return new DefaultTeamMigrationService(
                runRepository,
                lockRepository,
                teamService,
                authorizationPort,
                workspacePort,
                knowledgePort);
    }
}
