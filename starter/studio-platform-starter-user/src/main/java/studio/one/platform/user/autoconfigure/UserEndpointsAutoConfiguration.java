package studio.one.platform.user.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.web.mapper.ApplicationGroupMapperImpl;
import studio.one.base.user.web.mapper.ApplicationRoleMapperImpl;
import studio.one.base.user.web.mapper.ApplicationUserMapperImpl;
import studio.one.base.user.web.mapper.TimeMapperImpl;
import studio.one.base.user.domain.model.Group;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.controller.GroupController;
import studio.one.base.user.web.controller.MeController;
import studio.one.base.user.web.controller.RoleController;
import studio.one.base.user.web.controller.UserController;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.base.user.web.mapper.TimeMapper;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.security.authz.EndpointModeGuard;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

@Configuration
@EnableConfigurationProperties(WebProperties.class)
@AutoConfigureAfter(UserServicesAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class UserEndpointsAutoConfiguration {

    private static final String FEATURE_NAME = "User";
    private final WebProperties webProperties;
    private final I18n i18n;

    @Bean
    public UserDomainPolicyContributor userDomainPolicyContributor(WebProperties web){
       return  new UserDomainPolicyContributor(web);
    }

    @Bean
    @ConditionalOnMissingBean(EndpointModeGuard.class)
    public EndpointModeGuard endpointModeGuard(WebProperties web) {
        return new UserDomainEndpointModeGuard(web);
    }

    @Bean
    @ConditionalOnClass(TimeMapper.class)
    @ConditionalOnMissingBean(TimeMapper.class)
    public TimeMapper timeMapper() {
        return new TimeMapperImpl();
    }

    @Bean
    @ConditionalOnClass({ ApplicationGroupMapper.class, TimeMapper.class })
    @ConditionalOnMissingBean(ApplicationGroupMapper.class)
    public ApplicationGroupMapper groupMapper(TimeMapper timeMapper) {
        return new ApplicationGroupMapperImpl(timeMapper);
    }

    @Bean
    @ConditionalOnClass({ ApplicationUserMapper.class, TimeMapper.class })
    @ConditionalOnMissingBean(ApplicationUserMapper.class)
    public ApplicationUserMapper userMapper(TimeMapper timeMapper) {
        return new ApplicationUserMapperImpl(timeMapper);
    }

    @Bean
    @ConditionalOnClass({ ApplicationRoleMapper.class, TimeMapper.class })
    @ConditionalOnMissingBean(ApplicationRoleMapper.class)
    public ApplicationRoleMapper roleMapper(TimeMapper timeMapper) {
        return new ApplicationRoleMapperImpl(timeMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX  + ".group", name = "enabled", havingValue = "true")
    public GroupController groupEndpoint(
        ApplicationGroupService<Group, Role, User> svc, 
        ApplicationGroupMapper groupMapper,
        ApplicationUserMapper userMapper,
        ApplicationRoleMapper roleMapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationGroupService.class, true),
                LogUtils.blue(GroupController.class, true),
                webProperties.normalizedBasePath() + "/groups", webProperties.getEndpoints().getGroup().getMode()));
        return new GroupController(svc, groupMapper, userMapper, roleMapper);
    }
 
    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX
            + ".user", name = "enabled", havingValue = "true")
    public UserController userEndpoint(ApplicationUserService<User, Role> svc, ApplicationUserMapper mapper,  ApplicationRoleMapper roleMapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationUserService.class, true),
                LogUtils.blue(UserController.class, true),
                webProperties.normalizedBasePath() + "/users",
                webProperties.getEndpoints().getGroup().getMode()));
        return new UserController(svc, mapper, roleMapper) ;
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX
            + ".role", name = "enabled", havingValue = "true")
    public RoleController roleEndpoint(
        ApplicationRoleService svc, ApplicationRoleMapper mapper,
        ApplicationGroupMapper gmapper, ApplicationUserMapper umapper
        ) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationRoleService.class, true),
                LogUtils.blue(RoleController.class, true),
                webProperties.normalizedBasePath() + "/roles", webProperties.getEndpoints().getGroup().getMode()));
        return new RoleController(svc, mapper, gmapper, umapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Self.PREFIX , name = "enabled", havingValue = "true", matchIfMissing = true )
    public MeController selfEndpoint(ApplicationUserService<User, Role> svc, ApplicationUserMapper mapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue("Self"),
                LogUtils.blue(MeController.class, true),
                webProperties.getSelf().getPath()));
        return new MeController(svc, mapper);
    }
 
}