package studio.echo.platform.user.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.service.ApplicationGroupService;
import studio.echo.base.user.service.ApplicationRoleService;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.base.user.web.endpoint.GroupController;
import studio.echo.base.user.web.endpoint.UserController;
import studio.echo.base.user.web.mapper.ApplicationGroupMapper;
import studio.echo.base.user.web.mapper.ApplicationGroupMapperImpl;
import studio.echo.base.user.web.mapper.ApplicationUserMapper;
import studio.echo.base.user.web.mapper.ApplicationUserMapperImpl;
import studio.echo.base.user.web.mapper.TimeMapper;
import studio.echo.base.user.web.mapper.TimeMapperImpl;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.security.authz.EndpointModeGuard;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.LogUtils;

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
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX  + ".group", name = "enabled", havingValue = "true")
    public GroupController groupEndpoint(ApplicationGroupService svc, ApplicationGroupMapper mapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationGroupService.class, true),
                LogUtils.blue(GroupController.class, true),
                webProperties.normalizedBasePath() + "/groups", webProperties.getEndpoints().getGroup().getMode()));
        return new GroupController(svc, mapper);
    }

 
    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX
            + ".user", name = "enabled", havingValue = "true")
    public UserController userEndpoint(ApplicationUserService<User> svc, ApplicationUserMapper mapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationUserService.class, true),
                LogUtils.blue(UserController.class, true),
                webProperties.normalizedBasePath() + "/users",
                webProperties.getEndpoints().getGroup().getMode()));
        return new UserController(svc, mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX
            + ".role", name = "enabled", havingValue = "true")
    public RoleEndpoint roleEndpoint(ApplicationRoleService svc) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(ApplicationRoleService.class, true),
                LogUtils.blue(RoleEndpoint.class, true),
                webProperties.normalizedBasePath() + "/roles", webProperties.getEndpoints().getGroup().getMode()));
        return new RoleEndpoint();
    }

    @Getter
    @Setter
    public static class UserEndpoint {
    }

    @Getter
    @Setter
    public static class RoleEndpoint {
    }
}