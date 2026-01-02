package studio.one.application.mail.autoconfigure;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.application.mail.config.ImapProperties;
import studio.one.application.mail.domain.entity.MailAttachmentEntity;
import studio.one.application.mail.domain.entity.MailMessageEntity;
import studio.one.application.mail.persistence.repository.MailAttachmentRepository;
import studio.one.application.mail.persistence.repository.MailMessageRepository;
import studio.one.application.mail.persistence.repository.MailSyncLogRepository;
import studio.one.application.mail.service.MailAttachmentService;
import studio.one.application.mail.service.MailMessageService;
import studio.one.application.mail.service.MailSyncJobLauncher;
import studio.one.application.mail.service.MailSyncService;
import studio.one.application.mail.service.MailSyncLogService;
import studio.one.application.mail.service.MailSyncNotifier;
import studio.one.application.mail.service.SseMailSyncNotifier;
import studio.one.application.mail.service.impl.ImapMailSyncService;
import studio.one.application.mail.service.impl.JdbcMailAttachmentService;
import studio.one.application.mail.service.impl.JdbcMailMessageService;
import studio.one.application.mail.service.impl.JdbcMailSyncLogService;
import studio.one.application.mail.service.impl.JpaMailAttachmentService;
import studio.one.application.mail.service.impl.JpaMailMessageService;
import studio.one.application.mail.service.impl.JpaMailSyncLogService;
import studio.one.application.mail.web.controller.MailController;
import studio.one.application.mail.web.controller.MailSseController;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.application.mail.autoconfigure.condition.ConditionalOnMailPersistence;

@AutoConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".mail", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ MailFeatureProperties.class, PersistenceProperties.class })
public class MailAutoConfiguration {

    @Configuration
    @ConditionalOnClass(EntityManagerFactory.class)
    @ConditionalOnBean(name = "entityManagerFactory")
    @ConditionalOnMailPersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = { MailMessageRepository.class, MailAttachmentRepository.class })
    @EntityScan(basePackageClasses = { MailMessageEntity.class, MailAttachmentEntity.class })
    static class MailJpaConfig {
    }

    @Bean(MailMessageService.SERVICE_NAME)
    @ConditionalOnMissingBean(MailMessageService.class)
    public MailMessageService mailMessageService(
            MailFeatureProperties mailFeatureProperties,
            PersistenceProperties persistenceProperties,
            ObjectProvider<MailMessageRepository> jpaRepositoryProvider,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcProvider) {

        PersistenceProperties.Type type = mailFeatureProperties.resolvePersistence(persistenceProperties.getType());
        if (type == PersistenceProperties.Type.jpa) {
            MailMessageRepository repo = jpaRepositoryProvider.getIfAvailable();
            if (repo == null) {
                throw new IllegalStateException("JPA persistence selected but MailMessageRepository is not available");
            }
            return new JpaMailMessageService(repo);
        }
        if (type == PersistenceProperties.Type.jdbc) {
            NamedParameterJdbcTemplate jdbc = jdbcProvider.getIfAvailable();
            if (jdbc == null) {
                throw new IllegalStateException(
                        "JDBC persistence selected but NamedParameterJdbcTemplate is not available");
            }
            return new JdbcMailMessageService(jdbc);
        }
        throw new IllegalStateException("Unsupported persistence type for mail service: " + type);
    }

    @Bean(MailAttachmentService.SERVICE_NAME)
    @ConditionalOnMissingBean(MailAttachmentService.class)
    public MailAttachmentService mailAttachmentService(
            MailFeatureProperties mailFeatureProperties,
            PersistenceProperties persistenceProperties,
            ObjectProvider<MailAttachmentRepository> jpaRepositoryProvider,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcProvider) {

        PersistenceProperties.Type type = mailFeatureProperties.resolvePersistence(persistenceProperties.getType());
        if (type == PersistenceProperties.Type.jpa) {
            MailAttachmentRepository repo = jpaRepositoryProvider.getIfAvailable();
            if (repo == null) {
                throw new IllegalStateException(
                        "JPA persistence selected but MailAttachmentRepository is not available");
            }
            return new JpaMailAttachmentService(repo);
        }
        if (type == PersistenceProperties.Type.jdbc) {
            NamedParameterJdbcTemplate jdbc = jdbcProvider.getIfAvailable();
            if (jdbc == null) {
                throw new IllegalStateException(
                        "JDBC persistence selected but NamedParameterJdbcTemplate is not available");
            }
            return new JdbcMailAttachmentService(jdbc);
        }
        throw new IllegalStateException("Unsupported persistence type for mail attachment service: " + type);
    }

    @Bean(MailSyncLogService.SERVICE_NAME)
    @ConditionalOnMissingBean(MailSyncLogService.class)
    public MailSyncLogService mailSyncLogService(
            MailFeatureProperties mailFeatureProperties,
            PersistenceProperties persistenceProperties,
            ObjectProvider<MailSyncLogRepository> jpaRepositoryProvider,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcProvider) {

        PersistenceProperties.Type type = mailFeatureProperties.resolvePersistence(persistenceProperties.getType());
        if (type == PersistenceProperties.Type.jpa) {
            MailSyncLogRepository repo = jpaRepositoryProvider.getIfAvailable();
            if (repo == null) {
                throw new IllegalStateException("JPA persistence selected but MailSyncLogRepository is not available");
            }
            return new JpaMailSyncLogService(repo);
        }
        if (type == PersistenceProperties.Type.jdbc) {
            NamedParameterJdbcTemplate jdbc = jdbcProvider.getIfAvailable();
            if (jdbc == null) {
                throw new IllegalStateException(
                        "JDBC persistence selected but NamedParameterJdbcTemplate is not available");
            }
            return new JdbcMailSyncLogService(jdbc);
        }
        throw new IllegalStateException("Unsupported persistence type for mail sync log service: " + type);
    }

    @Bean(MailSyncService.SERVICE_NAME)
    @ConditionalOnMissingBean(MailSyncService.class)
    public MailSyncService mailSyncService(MailFeatureProperties properties,
            MailMessageService mailMessageService,
            MailAttachmentService mailAttachmentService,
            MailSyncLogService mailSyncLogService) {
        ImapProperties imap = properties.getImap();
        return new ImapMailSyncService(imap, mailMessageService, mailAttachmentService, mailSyncLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MailSyncJobLauncher mailSyncJobLauncher(MailSyncService mailSyncService,
            MailSyncLogService mailSyncLogService,
            MailSyncNotifier mailSyncNotifier) {
        return new MailSyncJobLauncher(mailSyncService, mailSyncLogService, mailSyncNotifier);
    }

    @Bean
    @ConditionalOnMissingBean
    @org.springframework.context.annotation.Conditional(MailSseCondition.class)
    public SseMailSyncNotifier mailSyncNotifier() {
        return new SseMailSyncNotifier();
    }

    @Configuration
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX  + ".mail.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import(MailController.class)
    static class MailWebConfig {

    }

    @Configuration
    @ConditionalOnProperties(prefix = PropertyKeys.Features.PREFIX + ".mail.web", value = {
            @ConditionalOnProperties.Property(name = "enabled", havingValue = "true", matchIfMissing = true)
    })
    @org.springframework.context.annotation.Conditional(MailSseCondition.class)
    @Import(MailSseController.class)
    static class MailWebSseConfig {

    }

    static class MailSseCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            String prefix = PropertyKeys.Features.PREFIX + ".mail.web.";
            String sse = env.getProperty(prefix + "sse");
            if (sse != null) {
                return Boolean.parseBoolean(sse);
            }
            String notify = env.getProperty(prefix + "notify");
            if (notify != null) {
                return "sse".equalsIgnoreCase(notify);
            }
            return true;
        }
    }
}
