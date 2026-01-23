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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.acls.model.MutableAclService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.persistence.AclClassRepository;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclObjectIdentityRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.base.security.acl.policy.AclPermissionMapper;
import studio.one.base.security.acl.policy.AclResourceMapper;
import studio.one.base.security.acl.policy.AclPolicyRefreshPublisher;
import studio.one.base.security.acl.policy.DatabaseAclDomainPolicyContributor;
import studio.one.base.security.acl.policy.DefaultAclPermissionMapper;
import studio.one.base.security.acl.policy.SimpleAclResourceMapper;
import studio.one.base.security.acl.service.DefaultAclPermissionService;
import studio.one.base.security.acl.service.RepositoryAclPermissionService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.authz.AllowAllEndpointModeGuard;
import studio.one.platform.security.acl.AclPermissionService;
import studio.one.platform.security.authz.DomainPolicyContributor;
import studio.one.platform.security.authz.DomainPolicyRegistry;
import studio.one.platform.security.authz.DomainPolicyRegistryImpl;
import studio.one.platform.security.authz.EndpointAuthorizationImpl;
import studio.one.platform.security.authz.EndpointModeGuard;
import studio.one.platform.security.acl.AclMetricsRecorder;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;
import studio.one.platform.security.acl.metrics.MicrometerAclMetricsRecorder;

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

        /** 
         * ServiceNames.DOMAIN_ENDPOINT_AUTHZ 이름으로 등록되어 동작.
         * 
         * SpEL: @endpointAuthz.can('domain','component','action') 
         * */
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

        @Bean
        @ConditionalOnClass(io.micrometer.core.instrument.MeterRegistry.class)
        @ConditionalOnMissingBean(AclMetricsRecorder.class)
        public AclMetricsRecorder micrometerAclMetricsRecorder(
                        io.micrometer.core.instrument.MeterRegistry meterRegistry,
                        SecurityAclProperties properties) {
                if (!properties.isMetricsEnabled()) {
                        return AclMetricsRecorder.noop();
                }
                return new MicrometerAclMetricsRecorder(meterRegistry);
        }

        @Bean
        @ConditionalOnMissingBean(AclMetricsRecorder.class)
        public AclMetricsRecorder aclMetricsRecorder() {
                return AclMetricsRecorder.noop();
        }

        @Bean
        @ConditionalOnMissingBean
        public AclPolicyRefreshPublisher aclPolicyRefreshPublisher(
                        ObjectProvider<DomainEvents> domainEventsProvider,
                        ObjectProvider<ApplicationEventPublisher> applicationEventPublisher) {
                return new AclPolicyRefreshPublisher(domainEventsProvider, applicationEventPublisher);
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
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX, name = "use-spring-acl", havingValue = "true")
        @ConditionalOnMissingBean
        public AclPermissionService aclPermissionService(MutableAclService aclService,
                        AclPolicyRefreshPublisher refreshPublisher,
                        ObjectProvider<AclMetricsRecorder> metricsRecorderProvider,
                        ObjectProvider<AclEntryRepository> entryRepositoryProvider,
                        ObjectProvider<AclObjectIdentityRepository> objectIdentityRepositoryProvider,
                        ObjectProvider<AclClassRepository> classRepositoryProvider,
                        ObjectProvider<AclSidRepository> sidRepositoryProvider,
                        SecurityAclProperties properties,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                FEATURE_NAME,
                                LogUtils.blue(DefaultAclPermissionService.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new DefaultAclPermissionService(
                                aclService,
                                metricsRecorderProvider.getIfAvailable(AclMetricsRecorder::noop),
                                refreshPublisher,
                                entryRepositoryProvider.getIfAvailable(),
                                objectIdentityRepositoryProvider.getIfAvailable(),
                                classRepositoryProvider.getIfAvailable(),
                                sidRepositoryProvider.getIfAvailable(),
                                properties.isAuditEnabled());
        }

        @Bean
        @ConditionalOnMissingBean(AclPermissionService.class)
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX, name = "use-spring-acl", havingValue = "false", matchIfMissing = true)
        public AclPermissionService repositoryAclPermissionService(
                        AclClassRepository classRepository,
                        AclSidRepository sidRepository,
                        AclObjectIdentityRepository objectIdentityRepository,
                        AclEntryRepository entryRepository,
                        AclPolicyRefreshPublisher refreshPublisher,
                        ObjectProvider<AclMetricsRecorder> metricsRecorderProvider,
                        SecurityAclProperties properties,
                        ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                                FEATURE_NAME,
                                LogUtils.blue(RepositoryAclPermissionService.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new RepositoryAclPermissionService(
                                classRepository,
                                sidRepository,
                                objectIdentityRepository,
                                entryRepository,
                                refreshPublisher,
                                metricsRecorderProvider.getIfAvailable(AclMetricsRecorder::noop),
                                properties.isAuditEnabled());
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

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(org.springframework.cache.CacheManager.class)
        @ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX, name = "use-spring-acl", havingValue = "true")
        static class AclCacheConfig {

                @Bean
                @ConditionalOnMissingBean
                public org.springframework.security.acls.domain.AclAuthorizationStrategy aclAuthorizationStrategy(
                                SecurityAclProperties properties) {
                        String adminRole = (properties.getAdminRole() == null || properties.getAdminRole().isBlank())
                                        ? "ROLE_ADMIN"
                                        : properties.getAdminRole();
                        return new org.springframework.security.acls.domain.AclAuthorizationStrategyImpl(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority(adminRole));
                }

                @Bean
                @ConditionalOnMissingBean
                public org.springframework.security.acls.model.PermissionGrantingStrategy permissionGrantingStrategy() {
                        return new org.springframework.security.acls.domain.DefaultPermissionGrantingStrategy(
                                        new org.springframework.security.acls.domain.ConsoleAuditLogger());
                }

                @Bean
                @ConditionalOnBean(org.springframework.cache.CacheManager.class)
                @ConditionalOnMissingBean(org.springframework.security.acls.model.AclCache.class)
                public org.springframework.security.acls.model.AclCache aclCache(
                                org.springframework.cache.CacheManager cacheManager,
                                org.springframework.security.acls.model.PermissionGrantingStrategy permissionGrantingStrategy,
                                org.springframework.security.acls.domain.AclAuthorizationStrategy aclAuthorizationStrategy,
                                SecurityAclProperties properties) {
                        String cacheName = (properties.getCacheName() == null || properties.getCacheName().isBlank())
                                        ? "aclCache"
                                        : properties.getCacheName();
                        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                        if (cache == null) {
                                cache = new org.springframework.cache.concurrent.ConcurrentMapCache(cacheName);
                        }
                        return new org.springframework.security.acls.domain.SpringCacheBasedAclCache(
                                        cache, permissionGrantingStrategy, aclAuthorizationStrategy);
                }

                @Bean
                @ConditionalOnBean(org.springframework.security.acls.model.AclCache.class)
                public studio.one.base.security.acl.policy.AclCacheInvalidationListener aclCacheInvalidationListener(
                                ObjectProvider<org.springframework.security.acls.model.AclCache> aclCacheProvider) {
                        return new studio.one.base.security.acl.policy.AclCacheInvalidationListener(aclCacheProvider);
                }
        }

}
