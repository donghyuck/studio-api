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
 *      @file SecurityAclDatabaseAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.acls.model.MutableAclService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.policy.AclPermissionMapper;
import studio.one.base.security.acl.policy.AclResourceMapper;
import studio.one.base.security.acl.policy.DatabaseAclDomainPolicyContributor;
import studio.one.base.security.acl.policy.DefaultAclPermissionMapper;
import studio.one.base.security.acl.policy.SimpleAclResourceMapper;
import studio.one.base.security.acl.service.AclPermissionService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.authz.AllowAllEndpointModeGuard;
import studio.one.platform.security.authz.DomainPolicyContributor;
import studio.one.platform.security.authz.DomainPolicyRegistry;
import studio.one.platform.security.authz.DomainPolicyRegistryImpl;
import studio.one.platform.security.authz.EndpointAuthorizationImpl;
import studio.one.platform.security.authz.EndpointModeGuard;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 * Auto-configuration that exposes the database ACL repositories and
 * contributors.
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

@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityAclProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
public class SecurityAclDatabaseAutoConfiguration {

        private static final String FEATURE_NAME = "Security - Acl";

        /**
         * SimpleAclResourceMapper를 생성해 도메인별 리소스 식별자/별칭/인디케이터를 매핑합니다.
         * ai.security.acl.domain-aliases/indicators 설정을 반영하며 생성.
         * 
         * @param i18nProvider
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        public AclResourceMapper aclResourceMapper(
                        SecurityAclProperties properties,
                        ObjectProvider<I18n> i18nProvider) {
                SimpleAclResourceMapper mapper = new SimpleAclResourceMapper();
                mapper.setDomainAliases(properties.getDomainAliases());
                mapper.setDomainIndicators(Objects.requireNonNullElseGet(
                                properties.getDomainIndicators(),
                                () -> Set.of("*", "__domain__", "__root__")));
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AclResourceMapper.class, true), LogUtils.red(State.CREATED.toString())));
                return mapper;
        }

        /**
         * 기본 구현인 DefaultAclPermissionMapper를 제공해 권한 코드 ↔ 비트마스크 매핑을 담당
         * 
         * @param i18nProvider
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        public AclPermissionMapper aclPermissionMapper(ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AclPermissionMapper.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new DefaultAclPermissionMapper();
        }

        /**
         * AclEntryRepository + AclResourceMapper + AclPermissionMapper를 묶어 DB 기반 ACL
         * 도메인 정책 제공자를 등록합니다. AclEntryRepository 빈이 있을 때만 활성화
         * 
         * @param repository
         * @param resourceMapper
         * @param permissionMapper
         * @param i18nProvider
         * @return
         */
        @Bean 
        public DomainPolicyContributor databaseAclDomainPolicyContributor(
                        AclEntryRepository repository,
                        AclResourceMapper resourceMapper,
                        AclPermissionMapper permissionMapper,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(DomainPolicyContributor.class, true),
                                LogUtils.red(State.CREATED.toString())));

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                                FEATURE_NAME,
                                LogUtils.blue(DomainPolicyContributor.class, true),
                                DatabaseAclDomainPolicyContributor.class.getSimpleName(),
                                LogUtils.green(repository.getClass(), true)));

                return new DatabaseAclDomainPolicyContributor(repository, resourceMapper, permissionMapper);
        }

        @Bean(name = ServiceNames.DOMAIN_POLICY_REGISTRY)
        @ConditionalOnMissingBean
        public DomainPolicyRegistry domainPolicyRegistry(
                        ObjectProvider<List<DomainPolicyContributor>> contributors, ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(DomainPolicyRegistryImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));

                List<DomainPolicyContributor> list = contributors.getIfAvailable(() -> List.of());
                if (list.isEmpty()) {
                        log.debug("{} - {}", FEATURE_NAME, LogUtils.red("no contributors found"));
                } else {
                        String joined = list.stream()
                                        .map(c -> LogUtils.green(c.getClass(), true))
                                        .collect(java.util.stream.Collectors.joining(", "));
                        log.debug("{} - {} {}", FEATURE_NAME, LogUtils.blue(DomainPolicyContributor.class, true), joined);
                }

                return new DomainPolicyRegistryImpl(contributors);
        }

        @Bean
        @ConditionalOnMissingBean(EndpointModeGuard.class)
        public EndpointModeGuard endpointModeGuard(ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AllowAllEndpointModeGuard.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new AllowAllEndpointModeGuard();
        }

        /** SpEL: @endpointAuthz.can('domain','component','action') */
        @Bean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
        @ConditionalOnBean({ DomainPolicyRegistry.class, EndpointModeGuard.class })
        @ConditionalOnMissingBean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
        public EndpointAuthorizationImpl endpointAuthorization(
                        DomainPolicyRegistry registry,
                        EndpointModeGuard endpointModeGuard,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(EndpointAuthorizationImpl.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new EndpointAuthorizationImpl(registry, endpointModeGuard);
        }

        /**
         * 존재하는 MutableAclService를 감싸 권한 조회/부여 등의 고수준 서비스를 제공. MutableAclService가
         * 클래스패스·빈 모두에 있을 때만 생성
         * 
         * @param aclService
         * @param i18nProvider
         * @return
         */
        @Bean
        @ConditionalOnClass(MutableAclService.class)
        @ConditionalOnBean(MutableAclService.class)
        @ConditionalOnMissingBean
        public AclPermissionService aclPermissionService(MutableAclService aclService,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                FEATURE_NAME,
                                LogUtils.blue(AclPermissionService.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new AclPermissionService(aclService);
        }
        @Configuration(proxyBeanMethods = false)
        @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
        static class EntityScanConfig {

                @Bean
                static BeanDefinitionRegistryPostProcessor securityAclDefaultEntityScan() {
                        return EntityScanRegistrarSupport.entityScanRegistrar(
                        PropertyKeys.Security.Acl.ENTITY_PACKAGES, 
                        SecurityAclProperties.DEFAULT_ENTITY_PACKAGE);
                }
        }

        @Configuration(proxyBeanMethods = false)
        @AutoConfigureAfter(EntityScanConfig.class)
        @ConditionalOnClass(EntityManagerFactory.class)
        @ConditionalOnBean(EntityManagerFactory.class)
        @EnableJpaRepositories(basePackages = "${" + PropertyKeys.Security.Acl.REPOSITORY_PACKAGES + ":" + SecurityAclProperties.DEFAULT_REPOSITORY_PACKAGE + "}")
        static class JpaWiring {

        }

}
