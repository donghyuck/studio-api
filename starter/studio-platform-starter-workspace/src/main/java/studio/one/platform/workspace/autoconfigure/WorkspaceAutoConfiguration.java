package studio.one.platform.workspace.autoconfigure;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.workspace.permission.WorkspacePermissionContributor;
import studio.one.platform.workspace.persistence.jpa.WorkspaceClosureJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceEntity;
import studio.one.platform.workspace.persistence.jpa.WorkspaceJpaRepository;
import studio.one.platform.workspace.persistence.jpa.WorkspaceMemberJpaRepository;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.service.impl.DefaultWorkspaceMemberService;
import studio.one.platform.workspace.service.impl.DefaultWorkspacePermissionService;
import studio.one.platform.workspace.service.impl.DefaultWorkspaceTreeService;
import studio.one.platform.workspace.service.impl.WorkspaceSettings;

@AutoConfiguration
@EnableConfigurationProperties({
        WorkspaceFeatureProperties.class,
        WorkspaceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "enabled", havingValue = "true")
public class WorkspaceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorkspaceSettings workspaceSettings(WorkspaceProperties properties, WorkspaceFeatureProperties featureProperties) {
        if (properties.getPermission().isDenyOverrideEnabled()) {
            throw new IllegalStateException("studio.workspace.permission.deny-override-enabled is reserved for a future release");
        }
        return new WorkspaceSettings(
                properties.getTree().getMaxDepth(),
                properties.getTree().getMaxChildrenPerNode(),
                properties.getSlug().getMaxLength(),
                properties.getPermission().isInheritParentRole(),
                featureProperties.isCompanyRequired());
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspacePermissionService workspacePermissionService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            ObjectProvider<WorkspacePermissionContributor> contributors,
            ObjectProvider<ApplicationCompanyMemberService> companyMemberServiceProvider,
            WorkspaceProperties properties,
            WorkspaceSettings settings) {
        ApplicationCompanyMemberService companyMemberService = properties.getPermission().isCompanyOwnerOverrideEnabled()
                ? companyMemberServiceProvider.getIfAvailable()
                : null;
        return new DefaultWorkspacePermissionService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                contributors.orderedStream().toList(),
                settings,
                companyMemberService);
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspaceTreeService workspaceTreeService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            WorkspaceSettings settings) {
        return new DefaultWorkspaceTreeService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                settings);
    }

    @Bean
    @ConditionalOnBean(WorkspaceJpaRepository.class)
    @ConditionalOnMissingBean
    WorkspaceMemberService workspaceMemberService(
            WorkspaceJpaRepository workspaceRepository,
            WorkspaceClosureJpaRepository closureRepository,
            WorkspaceMemberJpaRepository memberRepository,
            WorkspacePermissionService permissionService,
            EntityManager entityManager) {
        return new DefaultWorkspaceMemberService(
                workspaceRepository,
                closureRepository,
                memberRepository,
                permissionService,
                entityManager);
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor workspaceEntityScanRegistrar(Environment env) {
            String entityKey = PropertyKeys.Features.PREFIX + ".workspace.entity-packages";
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey, WorkspaceEntity.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackageClasses = {
            WorkspaceJpaRepository.class,
            WorkspaceClosureJpaRepository.class,
            WorkspaceMemberJpaRepository.class })
    static class WorkspaceJpaConfig {
    }
}
