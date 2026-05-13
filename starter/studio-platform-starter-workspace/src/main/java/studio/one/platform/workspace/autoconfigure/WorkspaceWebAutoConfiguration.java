package studio.one.platform.workspace.autoconfigure;

import org.springframework.context.annotation.Configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.web.controller.WorkspaceController;
import studio.one.platform.workspace.web.controller.WorkspaceMgmtController;

@Configuration
@AutoConfigureAfter(WorkspaceAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".workspace.web", name = "enabled", havingValue = "true")
@ConditionalOnBean({
        WorkspaceTreeService.class,
        WorkspaceMemberService.class,
        WorkspacePermissionService.class })
public class WorkspaceWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorkspaceController workspaceController(
            WorkspaceTreeService treeService,
            WorkspaceMemberService memberService,
            WorkspacePermissionService permissionService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new WorkspaceController(treeService, memberService, permissionService, principalResolverProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    WorkspaceMgmtController workspaceMgmtController(
            WorkspaceTreeService treeService,
            WorkspaceMemberService memberService,
            WorkspacePermissionService permissionService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        return new WorkspaceMgmtController(treeService, memberService, permissionService, principalResolverProvider);
    }
}
