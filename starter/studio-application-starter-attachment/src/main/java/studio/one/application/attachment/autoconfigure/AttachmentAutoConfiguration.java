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
 *      @file AttachmentAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.application.attachment.autoconfigure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.autoconfigure.condition.ConditionalOnAttachmentPersistence;
import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.persistence.jdbc.JdbcAttachmentRepository;
import studio.one.application.attachment.persistence.jpa.AttachmentDataJpaRepository;
import studio.one.application.attachment.persistence.jpa.AttachmentJpaRepository;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.attachment.service.AttachmentServiceImpl;
import studio.one.application.attachment.storage.CachedFileStore;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.storage.JdbcFileStore;
import studio.one.application.attachment.storage.JpaFileStore;
import studio.one.application.attachment.storage.LocalFileStore;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.service.Repository;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 *
 * @author donghyuck, son
 * @since 2025-11-26
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-26  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties({ AttachmentFeatureProperties.class, PersistenceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".attachment", name = "enabled", havingValue = "true")
@Slf4j
public class AttachmentAutoConfiguration {

    protected static final String FEATURE_NAME = "Attachment";

    @Bean
    @Primary
    @ConditionalOnMissingBean(FileStorage.class)
    FileStorage attachmentFileStorage(
            AttachmentFeatureProperties properties,
            ObjectProvider<Repository> repositoryProvider,
            ObjectProvider<I18n> i18nProvider,
            ObjectProvider<AttachmentDataJpaRepository> dataRepositoryProvider,
            ObjectProvider<NamedParameterJdbcTemplate> templateProvider,
            PersistenceProperties persistenceProperties) {
        AttachmentFeatureProperties.Storage storage = properties.getStorage();
        I18n i18n = I18nUtils.resolve(i18nProvider);

        if (storage.getType() == AttachmentFeatureProperties.Storage.Type.database) {
            PersistenceProperties.Type persistence = persistenceProperties.getType();
            if (persistence == PersistenceProperties.Type.jpa) {
                AttachmentDataJpaRepository repo = dataRepositoryProvider.getIfAvailable();
                if (repo == null) {
                    throw new IllegalStateException(
                            "AttachmentDataJpaRepository is required for database storage (JPA)");
                }
                FileStorage dbStore = new JpaFileStore(repo);
                if (storage.isCacheEnabled()) {
                    String baseDir = resolveBaseDir(storage, repositoryProvider.getIfAvailable());
                    ensureDirectory(baseDir, storage.isEnsureDirs());
                    FileStorage cache = new LocalFileStore(baseDir);
                    log.info("{} feature using cache base directory: {}", FEATURE_NAME, LogUtils.green(baseDir));
                    log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                            LogUtils.blue(CachedFileStore.class, true), LogUtils.red(State.CREATED.toString())));
                    return new CachedFileStore(dbStore, cache);
                }
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                        LogUtils.blue(JpaFileStore.class, true), LogUtils.red(State.CREATED.toString())));
                return dbStore;
            }
            NamedParameterJdbcTemplate template = templateProvider.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException("NamedParameterJdbcTemplate is required for database storage (JDBC)");
            }
            FileStorage dbStore = new JdbcFileStore(template);
            if (storage.isCacheEnabled()) {
                String baseDir = resolveBaseDir(storage, repositoryProvider.getIfAvailable());
                ensureDirectory(baseDir, storage.isEnsureDirs());
                FileStorage cache = new LocalFileStore(baseDir);
                log.info("{} feature using cache base directory: {}", FEATURE_NAME, LogUtils.green(baseDir));
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                        LogUtils.blue(CachedFileStore.class, true), LogUtils.red(State.CREATED.toString())));
                return new CachedFileStore(dbStore, cache);
            }
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                    LogUtils.blue(JdbcFileStore.class, true), LogUtils.red(State.CREATED.toString())));
            return dbStore;
        }

        String baseDir = resolveBaseDir(storage, repositoryProvider.getIfAvailable());
        ensureDirectory(baseDir, storage.isEnsureDirs());
        log.info("{} feature using base directory: {}", FEATURE_NAME, LogUtils.green(baseDir));
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(LocalFileStore.class, true), LogUtils.red(State.CREATED.toString())));

        return new LocalFileStore(baseDir);
    }

    @Bean(name = AttachmentService.SERVICE_NAME)
    @ConditionalOnMissingBean(name = AttachmentService.SERVICE_NAME)
    public AttachmentService attachmentService(
            AttachmentRepository attachmentRepository,
            FileStorage fileStorage,
            ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AttachmentServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));
        log.info("{} service ready with repository: {}", AttachmentService.class.getSimpleName(),
                LogUtils.green(attachmentRepository instanceof JdbcAttachmentRepository ? "JDBC" : "JPA"));
        return new AttachmentServiceImpl(attachmentRepository, fileStorage);
    }

    private String resolveBaseDir(AttachmentFeatureProperties.Storage storage, Repository repository) {
        if (StringUtils.hasText(storage.getBaseDir())) {
            return storage.getBaseDir();
        }
        if (repository != null) {
            try {
                return repository.getFile("attachments").getAbsolutePath();
            } catch (IOException ex) {
                log.warn("Failed to resolve attachment base dir from repository: {}", ex.getMessage());
            }
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "attachments").toString();
    }

    private void ensureDirectory(String baseDir, boolean ensureDirs) {
        if (!ensureDirs || !StringUtils.hasText(baseDir)) {
            return;
        }
        try {
            Files.createDirectories(Path.of(baseDir));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create attachment directory: " + baseDir, ex);
        }
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnAttachmentPersistence(PersistenceProperties.Type.jpa)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor entityScanRegistrar(Environment env) {
            String entityKey = PropertyKeys.Features.PREFIX + ".attachment.entity-packages";
            String packageName = ApplicationAttachment.class.getPackageName();
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey, packageName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnAttachmentPersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = { AttachmentJpaRepository.class, AttachmentDataJpaRepository.class })
    static class AttachmentJpaConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnAttachmentPersistence(PersistenceProperties.Type.jdbc)
    static class AttachmentJdbcConfig {

        @Bean
        @ConditionalOnMissingBean(AttachmentRepository.class)
        AttachmentRepository attachmentJdbcRepository(
                @Qualifier(ServiceNames.NAMED_JDBC_TEMPLATE) NamedParameterJdbcTemplate template) {
            return new JdbcAttachmentRepository(template);
        }
    }
}
