package studio.echo.platform.security.autoconfigure;

import java.util.concurrent.Executor;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;

import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.audit.LoginFailureEventListener;
import studio.echo.base.security.audit.LoginFailureLogRepository;
import studio.echo.base.security.audit.LoginFailureLogRetentionJob;
import studio.echo.base.security.audit.LoginSuccessEventListener;
import studio.echo.base.security.authentication.AccountLockService;
import studio.echo.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.component.State;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.util.LogUtils; 

@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "enabled", havingValue = "true")
@Slf4j
public class LoginFailureAuditAutoConfiguration {

    private static final String DEFAULT_ENTITY_PACKAGE = LoginFailureLogRepository.class.getPackageName();
    private static final String FEATURE_NAME = "Security - Login Failure Audit";


    @Bean( ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_PUBLISHER )
    @ConditionalOnMissingBean
    AuthenticationEventPublisher authenticationEventPublsher(
            org.springframework.context.ApplicationEventPublisher delegate,
            ObjectProvider<I18n> i18nProvider) {
        
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AuthenticationEventPublisher.class, true), LogUtils.red(State.CREATED.toString())));

        return new DefaultAuthenticationEventPublisher(delegate);
    }

    @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR)
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "async", havingValue = "true")
    @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR )
    public Executor loginFailureAuditExecutor(ObjectProvider<I18n> i18nProvider) {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(2);
        t.setMaxPoolSize(4);
        t.setThreadNamePrefix("login-failure-audit-");
        t.initialize();
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(ThreadPoolTaskExecutor.class, true), LogUtils.red(State.CREATED.toString())));

        return t;
    }

    @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
     @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER)
    public LoginFailureEventListener loginFailureEventListener(
            ObjectProvider<AccountLockService> accountLockService,
            ObjectProvider<LoginFailureLogRepository> loginFailureLogRepository,
            AuditProperties props, 
            ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(LoginFailureEventListener.class, true), LogUtils.red(State.CREATED.toString())));
        return new LoginFailureEventListener(accountLockService, loginFailureLogRepository);
    }

    @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_SUCCESS_EVENT_LISTENER)
     @ConditionalOnMissingBean(name = ServiceNames.SECURITY_AUDIT_LOGIN_SUCCESS_EVENT_LISTENER)
    public LoginSuccessEventListener loginSuccessEventListener(
            ObjectProvider<AccountLockService> accountLockService,
            ObjectProvider<LoginFailureLogRepository> loginFailureLogRepository,
            AuditProperties props, 
            ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(LoginSuccessEventListener.class, true), LogUtils.red(State.CREATED.toString())));
        return new LoginSuccessEventListener(accountLockService);
    }

    @Bean(name = ServiceNames.SECURITY_AUDIT_LOGIN_FAILURE_LOG_RETENTION_JOB)
    @ConditionalOnProperty(prefix = PropertyKeys.Security.Audit.LOGIN_FAILURE, name = "retention-days")
    public LoginFailureLogRetentionJob loginFailureLogRetentionJob(
            LoginFailureLogRepository repo,
            AuditProperties props,
            ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
        LogUtils.blue(LoginFailureLogRetentionJob.class, true), LogUtils.red(State.CREATED.toString())));
        log.debug("LoginFailureLogRetentionJob retention days: {}", props.getLoginFailure().getRetentionDays());
        return new LoginFailureLogRetentionJob(repo, props.getLoginFailure().getRetentionDays());
    }


    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class) 
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor auditEntityScanRegistrar(Environment env, I18n i18n) { 
            String entityKey = PropertyKeys.Security.Audit.LOGIN_FAILURE + ".entity-packages";
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, "Aduit", entityKey, DEFAULT_ENTITY_PACKAGE  ));
            return EntityScanRegistrarSupport.entityScanRegistrar(
                entityKey + ".entity-packages", DEFAULT_ENTITY_PACKAGE
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @EnableJpaRepositories(basePackages =  "studio.echo.base.security.audit" )
    static class JpaWiring {
    }
}
