package studio.one.platform.ai.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.RagTeamKnowledgeMigrationVerifier;
import studio.one.platform.ai.web.controller.TeamRagCitationGuard;
import studio.one.platform.ai.web.controller.TeamRagRetrievalService;
import studio.one.platform.ai.web.controller.TeamKnowledgeSourceController;
import studio.one.platform.ai.web.controller.DefaultTeamRagScopeResolver;
import studio.one.platform.ai.web.controller.ScopeBackedTeamCitationAuthorizer;
import studio.one.platform.ai.web.controller.PortableTeamMigrationKnowledgeAdapter;
import studio.one.platform.ai.core.rag.team.TeamCitationAuthorizer;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeMigrationVerifier;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.constant.PropertyKeys;

/** Optional bridge: base AI Web remains loadable without Team or Workspace artifacts. */
@AutoConfiguration(before = AiWebAutoConfiguration.class, afterName = {
        "studio.one.platform.team.autoconfigure.TeamAutoConfiguration",
        "studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration",
        "studio.one.platform.ai.autoconfigure.AiAutoConfiguration"})
@ConditionalOnClass(name = {
        "studio.one.platform.identity.PrincipalResolver",
        "studio.one.platform.team.application.usecase.TeamAuthorizationPort",
        "studio.one.platform.workspace.application.usecase.WorkspaceTreeService"})
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team", name = "enabled", havingValue = "true")
@Conditional(AiWebEndpointCondition.class)
@ConditionalOnBean({PrincipalResolver.class, TeamAuthorizationPort.class, WorkspaceTreeService.class, RagPipelineService.class})
public class AiTeamRagAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(TeamKnowledgeMigrationVerifier.class)
    TeamKnowledgeMigrationVerifier teamKnowledgeMigrationVerifier(RagPipelineService ragPipelineService) {
        return new RagTeamKnowledgeMigrationVerifier(ragPipelineService);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamMigrationKnowledgePort.class)
    TeamMigrationKnowledgePort teamMigrationKnowledgePort(
            PrincipalResolver principalResolver,
            WorkspaceTreeService workspaceTreeService,
            TeamAuthorizationPort teamAuthorization,
            ObjectProvider<TeamKnowledgeSourceContributor> contributors,
            RagPipelineService ragPipelineService,
            TeamKnowledgeMigrationVerifier migrationVerifier,
            ObjectMapper objectMapper,
            AiWebRagProperties properties) {
        return new PortableTeamMigrationKnowledgeAdapter(
                principalResolver,
                workspaceTreeService,
                teamAuthorization,
                contributors.orderedStream().toList(),
                ragPipelineService,
                migrationVerifier,
                objectMapper,
                properties.getRetrieval().getTeamMaxWorkspaces(),
                properties.getRetrieval().getTeamMaxObjectScopes());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamRagScopeResolver.class)
    TeamRagScopeResolver teamRagScopeResolver(
            PrincipalResolver principalResolver,
            TeamAuthorizationPort teamAuthorization,
            WorkspaceTreeService workspaceTreeService,
            ObjectProvider<TeamKnowledgeSourceContributor> contributors,
            AiWebRagProperties properties) {
        return new DefaultTeamRagScopeResolver(
                principalResolver,
                teamAuthorization,
                workspaceTreeService,
                contributors.orderedStream().toList(),
                properties.getRetrieval().getTeamMaxWorkspaces(),
                properties.getRetrieval().getTeamMaxObjectScopes());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamCitationAuthorizer.class)
    TeamCitationAuthorizer teamCitationAuthorizer(TeamRagScopeResolver scopeResolver) {
        return new ScopeBackedTeamCitationAuthorizer(scopeResolver);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamRagRetrievalService.class)
    TeamRagRetrievalService teamRagRetrievalService(
            RagPipelineService ragPipelineService,
            TeamRagScopeResolver scopeResolver,
            AiWebRagProperties properties) {
        return new TeamRagRetrievalService(
                ragPipelineService,
                scopeResolver,
                properties.getRetrieval().getTeamMaxObjectScopes());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamRagCitationGuard.class)
    TeamRagCitationGuard teamRagCitationGuard(
            TeamRagScopeResolver scopeResolver,
            TeamCitationAuthorizer citationAuthorizer) {
        return new TeamRagCitationGuard(scopeResolver, citationAuthorizer);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = PropertyKeys.Features.PREFIX + ".team",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(TeamKnowledgeSourceController.class)
    TeamKnowledgeSourceController teamKnowledgeSourceController(TeamRagScopeResolver scopeResolver) {
        return new TeamKnowledgeSourceController(scopeResolver);
    }

}
