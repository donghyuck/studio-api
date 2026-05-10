package studio.one.application.mail.autoconfigure;

import jakarta.persistence.EntityManagerFactory;

import java.util.List;

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

import studio.one.application.mail.infrastructure.config.ImapProperties;
import studio.one.application.mail.infrastructure.persistence.jpa.MailAttachmentEntity;
import studio.one.application.mail.infrastructure.persistence.jpa.MailMessageEntity;
import studio.one.application.mail.infrastructure.persistence.jpa.MailAttachmentRepository;
import studio.one.application.mail.infrastructure.persistence.jpa.MailMessageRepository;
import studio.one.application.mail.infrastructure.persistence.jpa.MailSyncLogRepository;
import studio.one.application.mail.application.service.CompositeMailSyncNotifier;
import studio.one.application.mail.application.usecase.MailAttachmentService;
import studio.one.application.mail.application.usecase.MailMessageService;
import studio.one.application.mail.application.usecase.MailSyncJobLauncher;
import studio.one.application.mail.application.usecase.MailSyncService;
import studio.one.application.mail.application.usecase.MailSyncLogService;
import studio.one.application.mail.application.usecase.MailSyncNotifier;
import studio.one.application.mail.application.service.SseMailSyncNotifier;
import studio.one.application.mail.application.service.ImapMailSyncService;
import studio.one.application.mail.application.service.JdbcMailAttachmentService;
import studio.one.application.mail.application.service.JdbcMailMessageService;
import studio.one.application.mail.application.service.JdbcMailSyncLogService;
import studio.one.application.mail.application.service.JpaMailAttachmentService;
import studio.one.application.mail.application.service.JpaMailMessageService;
import studio.one.application.mail.application.service.JpaMailSyncLogService;
import studio.one.application.mail.web.controller.MailController;
import studio.one.application.mail.web.controller.MailSseController;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.jdbc.JdbcDatabaseSupport;
import studio.one.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.application.mail.autoconfigure.condition.ConditionalOnMailPersistence;

@AutoConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".mail", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ MailFeatureProperties.class, PersistenceProperties.class })
@Import(MailImapPropertiesConfiguration.class)
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

        PersistenceProperties.Type type = resolveMailPersistence(mailFeatureProperties, persistenceProperties);
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
            JdbcDatabaseSupport.requirePostgreSQL(jdbc, "mail message");
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

        PersistenceProperties.Type type = resolveMailPersistence(mailFeatureProperties, persistenceProperties);
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
            JdbcDatabaseSupport.requirePostgreSQL(jdbc, "mail attachment");
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

        PersistenceProperties.Type type = resolveMailPersistence(mailFeatureProperties, persistenceProperties);
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
            JdbcDatabaseSupport.requirePostgreSQL(jdbc, "mail sync log");
            return new JdbcMailSyncLogService(jdbc);
        }
        throw new IllegalStateException("Unsupported persistence type for mail sync log service: " + type);
    }

    @Bean(MailSyncService.SERVICE_NAME)
    @ConditionalOnMissingBean(MailSyncService.class)
    public MailSyncService mailSyncService(ImapProperties imap,
            MailMessageService mailMessageService,
            MailAttachmentService mailAttachmentService,
            MailSyncLogService mailSyncLogService) {
        return new ImapMailSyncService(imap, mailMessageService, mailAttachmentService, mailSyncLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MailSyncJobLauncher mailSyncJobLauncher(MailSyncService mailSyncService,
            MailSyncLogService mailSyncLogService,
            ObjectProvider<MailSyncNotifier> mailSyncNotifiers) {
        List<MailSyncNotifier> notifiers = mailSyncNotifiers.orderedStream().toList();
        return new MailSyncJobLauncher(mailSyncService, mailSyncLogService, new CompositeMailSyncNotifier(notifiers));
    }

    @Bean
    @ConditionalOnMissingBean(SseMailSyncNotifier.class)
    @org.springframework.context.annotation.Conditional(MailSseCondition.class)
    public SseMailSyncNotifier sseMailSyncNotifier() {
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

    private static PersistenceProperties.Type resolveMailPersistence(
            MailFeatureProperties mailFeatureProperties,
            PersistenceProperties persistenceProperties) {
        PersistenceProperties.Type type = mailFeatureProperties.resolvePersistence(persistenceProperties.getType());
        return type == PersistenceProperties.Type.mybatis ? PersistenceProperties.Type.jdbc : type;
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
            return true;
        }
    }
}
