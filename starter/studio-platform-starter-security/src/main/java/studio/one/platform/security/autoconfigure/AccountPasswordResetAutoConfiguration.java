package studio.one.platform.security.autoconfigure;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.InitializingBean;
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
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.aplication.security.auth.password.MailService;
import studio.one.aplication.security.auth.password.PasswordResetService;
import studio.one.aplication.security.auth.password.impl.MailServiceImpl;
import studio.one.aplication.security.web.controller.PasswordResetController;
import studio.one.base.security.jwt.reset.domain.PasswordResetToken;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;
import studio.one.base.security.jwt.reset.persistence.jdbc.PasswordResetTokenJdbcRepositoryV2;
import studio.one.base.security.jwt.reset.persistence.jpa.PasswordResetTokenJpaRepository;
import studio.one.base.security.web.controller.JwtAuthController;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.security.autoconfigure.condition.ConditionalOnPasswordResetPersistence;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties({ AccountPasswordResetProperties.class, PersistenceProperties.class })
@ConditionalOnClass({ JavaMailSender.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.Auth.PASSWORD_RESET, name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AccountPasswordResetAutoConfiguration {

    private static final String FEATURE_NAME = "Security - Password Reset";
    private static final String[] DEFAULT_JPA_ENTITY_PACKAGES = { PasswordResetToken.class.getPackageName() };
    private final ObjectProvider<I18n> i18nProvider;

    @Bean
    @ConditionalOnMissingBean
    InitializingBean passwordResetRepositoryLogger(
            PasswordResetTokenRepository repository,
            AccountPasswordResetProperties properties,
            PersistenceProperties persistenceProperties) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        return () -> {
            var globalPersistence = persistenceProperties.getType();
            var resetPersistence = properties.resolvePersistence(globalPersistence);
            if (resetPersistence == PersistenceProperties.Type.jpa
                    && globalPersistence != PersistenceProperties.Type.jpa) {
                throw new IllegalStateException("""
                        Password reset persistence is set to JPA but studio.persistence.type=%s.
                        Enable JPA persistence or change security.auth.password-reset.persistence to jdbc."""
                        .formatted(globalPersistence));
            }
            boolean isJdbc = repository instanceof PasswordResetTokenJdbcRepositoryV2;
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                    LogUtils.blue(PasswordResetTokenRepository.class, true), LogUtils.red(State.CREATED.toString())));
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.INFO + I18nKeys.AutoConfig.Feature.Service.INIT,
                    FEATURE_NAME,
                    LogUtils.blue(PasswordResetTokenRepository.class, true),
                    "PasswordResetTokenRepository",
                    LogUtils.green(
                            isJdbc ? PasswordResetTokenJdbcRepositoryV2.class : PasswordResetTokenJpaRepository.class,
                            true)));
        };
    }

    @Bean(PasswordResetService.SERVICE_NAME)
    @ConditionalOnMissingBean
    PasswordResetService passwordResetService(
            ApplicationUserService<? extends User, ? extends Role> userService,
            PasswordResetTokenRepository repository,
            MailService mailService) {
        @SuppressWarnings("unchecked")

        ApplicationUserService<User, Role> casted = (ApplicationUserService<User, Role>) userService;
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(PasswordResetService.class, true), LogUtils.red(State.CREATED.toString())));

        return new PasswordResetService(casted, repository, mailService);
    }

    @Bean(MailService.SERVICE_NAME)
    @ConditionalOnMissingBean
    MailService mailService(AccountPasswordResetProperties properties, JavaMailSender mailSender) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(MailServiceImpl.class, true), LogUtils.red(State.CREATED.toString())));

        return new MailServiceImpl(properties.getResetPasswordUrl(), mailSender);
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordResetController passwordResetController(AccountPasswordResetProperties props,
            PasswordResetService passwordResetService) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue("Password Reset Controller"),
                LogUtils.blue(PasswordResetController.class, true),
                props.getWeb().getBasePath(), "CRUD"));

        return new PasswordResetController(passwordResetService);
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnPasswordResetPersistence(PersistenceProperties.Type.jpa)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor passwordResetEntityScanRegistrar(Environment env, I18n i18n) {
            String entityKey = PropertyKeys.Security.Auth.PASSWORD_RESET + ".entity-packages";
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, FEATURE_NAME, entityKey,
                    String.join(", ", DEFAULT_JPA_ENTITY_PACKAGES)));
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey, DEFAULT_JPA_ENTITY_PACKAGES);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnPasswordResetPersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = PasswordResetTokenJpaRepository.class)
    static class JpaWiring {

    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnPasswordResetPersistence(PersistenceProperties.Type.jdbc)
    static class JdbcWiring {

        @Bean
        @ConditionalOnMissingBean(PasswordResetTokenRepository.class)
        PasswordResetTokenRepository passwordResetTokenJdbcRepository(NamedParameterJdbcTemplate template) {
            return new PasswordResetTokenJdbcRepositoryV2(template);
        }
    }

}
