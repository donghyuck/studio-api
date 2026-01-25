/**
 *
 *      Copyright 2026
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ObjectTypeAutoConfiguration.java
 *      @date 2026
 *
 */

package studio.one.platform.autoconfigure.objecttype;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import javax.persistence.EntityManagerFactory;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ResourceLoader;
import org.springframework.context.annotation.Primary;

import studio.one.platform.objecttype.lifecycle.ObjectRebindService;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;
import studio.one.platform.objecttype.yaml.YamlObjectPolicyResolver;
import studio.one.platform.objecttype.yaml.YamlObjectRebindService;
import studio.one.platform.objecttype.yaml.YamlObjectTypeRegistry;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.objecttype.condition.ConditionalOnObjectTypePersistence;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.objecttype.db.jdbc.JdbcObjectPolicyResolver;
import studio.one.platform.objecttype.db.jdbc.JdbcObjectTypeRegistry;
import studio.one.platform.objecttype.db.jdbc.ObjectTypeJdbcRepository;
import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.jpa.JpaObjectTypeStore;
import studio.one.platform.objecttype.db.jpa.entity.ObjectTypeEntity;
import studio.one.platform.objecttype.db.jpa.repo.ObjectTypeJpaRepository;
import studio.one.platform.objecttype.db.jpa.repo.ObjectTypePolicyJpaRepository;
import studio.one.platform.objecttype.db.jpa.service.JpaObjectPolicyResolver;
import studio.one.platform.objecttype.db.jpa.service.JpaObjectTypeRegistry;
import studio.one.platform.objecttype.cache.CachedObjectPolicyResolver;
import studio.one.platform.objecttype.cache.CachedObjectTypeRegistry;
import studio.one.platform.objecttype.cache.CachedObjectRebindService;
import studio.one.platform.objecttype.cache.CacheInvalidatable;
import java.time.Duration;

import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

import studio.one.platform.objecttype.service.DefaultObjectTypeAdminService;
import studio.one.platform.objecttype.service.DefaultObjectTypeRuntimeService;
import studio.one.platform.objecttype.service.ObjectTypeAdminService;
import studio.one.platform.objecttype.service.ObjectTypeRuntimeService;

@AutoConfiguration
@EnableConfigurationProperties({ ObjectTypeFeatureProperties.class, ObjectTypeProperties.class })
@ConditionalOnProperty(prefix = "studio.features.objecttype", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ObjectTypeAutoConfiguration {

    protected static final String FEATURE_NAME = "OBJECT TYPE";
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "yaml", matchIfMissing = true)
    public YamlObjectTypeLoader yamlObjectTypeLoader(ResourceLoader resourceLoader) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(YamlObjectTypeLoader.class, true), LogUtils.red(State.CREATED.toString())));

        return new YamlObjectTypeLoader(resourceLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "yaml", matchIfMissing = true)
    public ObjectTypeRegistry objectTypeRegistry(ObjectTypeProperties properties, YamlObjectTypeLoader loader) {
        YamlObjectTypeLoader.Result result = loader.load(properties.getYaml().getResource());

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(YamlObjectTypeRegistry.class, true), LogUtils.red(State.CREATED.toString())));

        return new YamlObjectTypeRegistry(result.byType(), result.byKey());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "yaml", matchIfMissing = true)
    public ObjectPolicyResolver objectPolicyResolver(ObjectTypeProperties properties, YamlObjectTypeLoader loader) {
        YamlObjectTypeLoader.Result result = loader.load(properties.getYaml().getResource());

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(YamlObjectPolicyResolver.class, true), LogUtils.red(State.CREATED.toString())));

        return new YamlObjectPolicyResolver(result.policies());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "yaml", matchIfMissing = true)
    public ObjectRebindService objectRebindService() {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(YamlObjectPolicyResolver.class, true), LogUtils.red(State.CREATED.toString())));

        return new YamlObjectRebindService();
    }

    @Bean
    @Primary
    @ConditionalOnBean(ObjectTypeRegistry.class)
    @ConditionalOnProperty(prefix = "studio.objecttype.registry.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ObjectTypeRegistry cachedObjectTypeRegistry(ObjectTypeRegistry registry, ObjectTypeProperties properties) {
        Duration ttl = Duration.ofSeconds(properties.getRegistry().getCache().getTtlSeconds());
        long maxSize = properties.getRegistry().getCache().getMaxSize();

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(CachedObjectTypeRegistry.class, true), LogUtils.red(State.CREATED.toString())));

        return new CachedObjectTypeRegistry(registry, ttl, maxSize);
    }

    @Bean
    @Primary
    @ConditionalOnBean(ObjectPolicyResolver.class)
    @ConditionalOnProperty(prefix = "studio.objecttype.policy.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ObjectPolicyResolver cachedObjectPolicyResolver(ObjectPolicyResolver resolver,
            ObjectTypeProperties properties) {
        Duration ttl = Duration.ofSeconds(properties.getPolicy().getCache().getTtlSeconds());
        long maxSize = properties.getPolicy().getCache().getMaxSize();

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(CachedObjectPolicyResolver.class, true), LogUtils.red(State.CREATED.toString())));

        return new CachedObjectPolicyResolver(resolver, ttl, maxSize);
    }

    @Bean(ObjectTypeRuntimeService.SERVICE_NAME)
    @ConditionalOnMissingBean
    public ObjectTypeRuntimeService objectTypeRuntimeService(ObjectTypeRegistry registry,
            ObjectPolicyResolver resolver) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(DefaultObjectTypeRuntimeService.class, true), LogUtils.red(State.CREATED.toString())));

        return new DefaultObjectTypeRuntimeService(registry, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectRebindService defaultObjectRebindService() {
        return new ObjectRebindService() {
            @Override
            public void rebind() {
            }

            @Override
            public void rebind(int objectType) {
            }

            @Override
            public void cleanup() {
            }
        };
    }

    @Bean
    @Primary
    @ConditionalOnBean(ObjectRebindService.class)
    public ObjectRebindService cachedObjectRebindService(
            ObjectRebindService delegate,
            ObjectTypeRegistry registry,
            ObjectPolicyResolver resolver) {
        CacheInvalidatable registryCache = (registry instanceof CacheInvalidatable ci) ? ci : null;
        CacheInvalidatable policyCache = (resolver instanceof CacheInvalidatable ci) ? ci : null;
        if (registryCache == null && policyCache == null) {
            return delegate;
        }
        return new CachedObjectRebindService(delegate, registryCache, policyCache);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "db")
    @ConditionalOnClass({ PersistenceProperties.class, EntityManagerFactory.class })
    @ConditionalOnBean(name = "entityManagerFactory")
    @ConditionalOnObjectTypePersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = ObjectTypeJpaRepository.class)
    @EntityScan(basePackageClasses = ObjectTypeEntity.class)
    @Slf4j
    @RequiredArgsConstructor
    static class ObjectTypeJpaConfig {

        private final ObjectProvider<I18n> i18nProvider;

        @Bean
        @ConditionalOnMissingBean
        public JpaObjectTypeRegistry objectTypeRegistry(ObjectTypeJpaRepository repository) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(JpaObjectTypeRegistry.class, true), LogUtils.red(State.CREATED.toString())));
            return new JpaObjectTypeRegistry(repository);
        }

        @Bean
        @ConditionalOnMissingBean
        public JpaObjectPolicyResolver objectPolicyResolver(ObjectTypePolicyJpaRepository repository) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(JpaObjectPolicyResolver.class, true), LogUtils.red(State.CREATED.toString())));
            return new JpaObjectPolicyResolver(repository);
        }

        @Bean
        @ConditionalOnMissingBean
        public ObjectTypeStore objectTypeStore(ObjectTypeJpaRepository typeRepository,
                ObjectTypePolicyJpaRepository policyRepository,
                javax.persistence.EntityManager entityManager) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(JpaObjectTypeStore.class, true), LogUtils.red(State.CREATED.toString())));
            return new JpaObjectTypeStore(typeRepository, policyRepository, entityManager);
        }

        @Bean(name = ObjectTypeAdminService.SERVICE_NAME)
        @ConditionalOnMissingBean
        public ObjectTypeAdminService objectTypeAdminService(ObjectTypeStore store) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(DefaultObjectTypeAdminService.class, true), LogUtils.red(State.CREATED.toString())));
            return new DefaultObjectTypeAdminService(store);
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "studio.objecttype", name = "mode", havingValue = "db")
    @ConditionalOnClass({ PersistenceProperties.class, NamedParameterJdbcTemplate.class })
    @ConditionalOnObjectTypePersistence(PersistenceProperties.Type.jdbc)
    @Slf4j
    @RequiredArgsConstructor
    static class ObjectTypeJdbcConfig {

        private final ObjectProvider<I18n> i18nProvider;

        @Bean
        @ConditionalOnMissingBean
        public ObjectTypeJdbcRepository objectTypeJdbcRepository(
                NamedParameterJdbcTemplate template) {

            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(ObjectTypeJdbcRepository.class, true), LogUtils.red(State.CREATED.toString())));

            return new ObjectTypeJdbcRepository(template);
        }

        @Bean
        @ConditionalOnMissingBean
        public JdbcObjectTypeRegistry objectTypeRegistry(ObjectTypeJdbcRepository repository) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(JdbcObjectTypeRegistry.class, true), LogUtils.red(State.CREATED.toString())));
            return new JdbcObjectTypeRegistry(repository);
        }

        @Bean
        @ConditionalOnMissingBean
        public JdbcObjectPolicyResolver objectPolicyResolver(ObjectTypeJdbcRepository repository) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(JdbcObjectPolicyResolver.class, true), LogUtils.red(State.CREATED.toString())));
            return new JdbcObjectPolicyResolver(repository);
        }

        @Bean(ObjectTypeStore.SERVICE_NAME)
        @ConditionalOnMissingBean
        public ObjectTypeStore objectTypeStore(ObjectTypeJdbcRepository repository) {
            return repository;
        }

        @Bean(ObjectTypeAdminService.SERVICE_NAME)
        @ConditionalOnMissingBean
        public ObjectTypeAdminService objectTypeAdminService(ObjectTypeStore store) {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                    ObjectTypeAutoConfiguration.FEATURE_NAME,
                    LogUtils.blue(DefaultObjectTypeAdminService.class, true), LogUtils.red(State.CREATED.toString())));
            return new DefaultObjectTypeAdminService(store);
        }
    }

    // Web config moved to ObjectTypeWebAutoConfiguration to ensure condition evaluation happens after
    // ObjectTypeAutoConfiguration bean definitions are registered.
}
