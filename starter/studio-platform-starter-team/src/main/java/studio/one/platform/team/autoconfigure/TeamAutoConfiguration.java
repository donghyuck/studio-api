package studio.one.platform.team.autoconfigure;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.team.application.service.DefaultTeamAuthorizationService;
import studio.one.platform.team.application.service.DefaultTeamMemberService;
import studio.one.platform.team.application.service.DefaultTeamJoinService;
import studio.one.platform.team.application.service.DefaultTeamService;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamCompanyAssignmentPort;
import studio.one.platform.team.application.usecase.TeamMemberService;
import studio.one.platform.team.application.usecase.TeamJoinService;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.application.usecase.TeamWorkspaceProvisioningPort;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamEntity;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamJoinRequestJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMemberJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationLockJpaRepository;
import studio.one.platform.team.infrastructure.persistence.jpa.TeamMigrationRunJpaRepository;

@AutoConfiguration
@EnableConfigurationProperties(TeamFeatureProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team", name = "enabled", havingValue = "true")
public class TeamAutoConfiguration {

    @Bean
    @ConditionalOnBean(TeamJpaRepository.class)
    @ConditionalOnMissingBean
    TeamAuthorizationPort teamAuthorizationPort(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository) {
        return new DefaultTeamAuthorizationService(teamRepository, memberRepository);
    }

    @Bean
    @ConditionalOnBean({ ApplicationCompanyService.class, ApplicationCompanyPermissionService.class })
    @ConditionalOnMissingBean
    TeamCompanyAssignmentPort teamCompanyAssignmentPort(
            ApplicationCompanyService companyService,
            ApplicationCompanyPermissionService permissionService) {
        return (companyId, actor) -> {
            companyService.get(companyId);
            if (!actor.platformAdmin()) {
                permissionService.assertGranted(
                        companyId,
                        actor.requireUserId(),
                        CompanyPermissionActions.WORKSPACE_CREATE);
            }
        };
    }

    @Bean
    @ConditionalOnBean({ TeamJpaRepository.class, TeamAuthorizationPort.class })
    @ConditionalOnMissingBean
    TeamService teamService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamAuthorizationPort authorizationPort,
            ObjectProvider<TeamCompanyAssignmentPort> companyAssignmentPortProvider,
            ObjectProvider<TeamWorkspaceProvisioningPort> workspaceProvisioningPortProvider) {
        return new DefaultTeamService(
                teamRepository,
                memberRepository,
                authorizationPort,
                companyAssignmentPortProvider.getIfAvailable(),
                workspaceProvisioningPortProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean({ TeamJpaRepository.class, TeamAuthorizationPort.class })
    @ConditionalOnMissingBean
    TeamMemberService teamMemberService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamAuthorizationPort authorizationPort) {
        return new DefaultTeamMemberService(teamRepository, memberRepository, authorizationPort);
    }

    @Bean
    @ConditionalOnBean({ TeamJpaRepository.class, TeamAuthorizationPort.class })
    @ConditionalOnMissingBean
    TeamJoinService teamJoinService(
            TeamJpaRepository teamRepository,
            TeamMemberJpaRepository memberRepository,
            TeamJoinRequestJpaRepository requestRepository,
            TeamAuthorizationPort authorizationPort) {
        return new DefaultTeamJoinService(
                teamRepository, memberRepository, requestRepository, authorizationPort);
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor teamEntityScanRegistrar(Environment environment) {
            return EntityScanRegistrarSupport.entityScanRegistrar(
                    PropertyKeys.Features.PREFIX + ".team.entity-packages",
                    TeamEntity.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".team", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackageClasses = {
            TeamJpaRepository.class,
            TeamMemberJpaRepository.class,
            TeamJoinRequestJpaRepository.class,
            TeamMigrationRunJpaRepository.class,
            TeamMigrationLockJpaRepository.class })
    static class TeamJpaConfig {
    }
}
