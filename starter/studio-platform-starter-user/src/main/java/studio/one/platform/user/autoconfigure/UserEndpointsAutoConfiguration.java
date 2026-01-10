/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file UserEndpointsAutoConfiguration.java
 *      @date 2025
 *
 */

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
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.controller.GroupController;
import studio.one.base.user.web.controller.MeController;
import studio.one.base.user.web.controller.PublicUserController;
import studio.one.base.user.web.controller.RoleController;
import studio.one.base.user.web.controller.UserController;
import studio.one.base.user.web.mapper.ApplicationGroupMapper;
import studio.one.base.user.web.mapper.ApplicationGroupMapperImpl;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationRoleMapperImpl;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapperImpl;
import studio.one.base.user.web.mapper.TimeMapper;
import studio.one.base.user.web.mapper.TimeMapperImpl;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

/**
 *
 * @author donghyuck, son
 * @since 2025-11-11
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-11  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Configuration
@EnableConfigurationProperties(WebProperties.class)
@AutoConfigureAfter(UserServicesAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.PREFIX, name = "enabled", havingValue = "true" , matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class UserEndpointsAutoConfiguration {
 
    private final WebProperties webProperties;
    private final I18n i18n;

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
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX + ".group", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GroupController groupEndpoint(
            ApplicationGroupService svc,
            ApplicationGroupMapper groupMapper,
            ApplicationUserMapper userMapper,
            ApplicationRoleMapper roleMapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, UserServicesAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ApplicationGroupService.class, true),
                LogUtils.blue(GroupController.class, true),
                webProperties.normalizedBasePath() + "/groups", LogUtils.blue("ACL-managed")));
        return new GroupController(svc, groupMapper, userMapper, roleMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX + ".user", name = "enabled", havingValue = "true", matchIfMissing = true )
    public UserController userEndpoint(
            ApplicationUserService svc,
            ApplicationUserMapper mapper,
            ApplicationRoleMapper roleMapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, UserServicesAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ApplicationUserService.class, true),
                LogUtils.blue(UserController.class, true),
                webProperties.normalizedBasePath() + "/users",
                LogUtils.blue("ACL-managed")));
        return new UserController(svc, mapper, roleMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX + ".public", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PublicUserController publicUserEndpoint(
            ApplicationUserService svc,
            ApplicationUserMapper mapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, UserServicesAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ApplicationUserService.class, true),
                LogUtils.blue(PublicUserController.class, true),
                "/api/public/users",
                "R"));
        return new PublicUserController(svc, mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Endpoints.PREFIX  + ".role", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RoleController roleEndpoint(
            ApplicationRoleService svc,
            ApplicationRoleMapper mapper,
            ApplicationGroupMapper gmapper,
            ApplicationUserMapper umapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, UserServicesAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(ApplicationRoleService.class, true),
                LogUtils.blue(RoleController.class, true),
                webProperties.normalizedBasePath() + "/roles",
                LogUtils.blue("ACL-managed")));
        return new RoleController(svc, mapper, gmapper, umapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.User.Web.Self.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeController selfEndpoint(
            ApplicationUserService svc,
            ApplicationUserMapper mapper) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, UserServicesAutoConfiguration.FEATURE_NAME,
                LogUtils.blue("Self"),
                LogUtils.blue(MeController.class, true),
                webProperties.getSelf().getPath()),
                LogUtils.blue("ACL-managed"));
        return new MeController(svc, mapper);
    }

}
