package studio.one.platform.security.autoconfigure;

import java.time.Clock;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.AccountLockService;
import studio.one.base.security.authentication.AccountLockServiceImpl;
import studio.one.base.security.authentication.lock.AccountLockJdbcRepository;
import studio.one.base.security.authentication.lock.AccountLockJpaRepository;
import studio.one.base.security.authentication.lock.AccountLockRepository;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.autoconfigure.condition.ConditionalOnAccountLockPersistence;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties({ AccountLockProperties.class, PersistenceProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Security.Auth.LOCK, name = "enabled", havingValue = "true")
@Slf4j
public class AccountLockAutoConfiguration {

        private static final String FEATURE_NAME = "Security - Account Lock";

        @Bean(ServiceNames.SECURITY_ACCOUNT_LOCK_SERVICE)
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_ACCOUNT_LOCK_SERVICE)
        public AccountLockService accountLockService(
                        AccountLockRepository accountLockRepository,
                        AccountLockProperties properties,
                        Clock clock,
                        ObjectProvider<I18n> i18nProvider,
                        PersistenceProperties persistenceProperties) {

                I18n i18n = I18nUtils.resolve(i18nProvider);

                PersistenceProperties.Type globalPersistence = persistenceProperties.getType();
                PersistenceProperties.Type lockPersistence = properties.resolvePersistence(globalPersistence);

                if (lockPersistence == PersistenceProperties.Type.jpa
                                && globalPersistence != PersistenceProperties.Type.jpa) {
                        throw new IllegalStateException(
                                        """
                                                        Account lock persistence is set to JPA but studio.persistence.type=%s. \
                                                        Enable JPA persistence for the user module or change security.auth.lock.persistence to jdbc."""
                                                        .formatted(globalPersistence));
                }

                boolean isJdbc = accountLockRepository instanceof AccountLockJdbcRepository;

                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AccountLockService.class, true), LogUtils.red(State.CREATED.toString())));
                log.info("Using AccountLockRepository: {}", LogUtils.green(
                                isJdbc ? AccountLockJdbcRepository.class : AccountLockJpaRepository.class, true));

                return new AccountLockServiceImpl(
                                accountLockRepository,
                                clock,
                                properties.getMaxAttempts(),
                                properties.getWindow(),
                                properties.getLockDuration(),
                                properties.isResetOnSuccess());

        }

        @Configuration(proxyBeanMethods = false)
        @AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
        @ConditionalOnAccountLockPersistence(PersistenceProperties.Type.jpa)
        @ConditionalOnBean(EntityManagerFactory.class)
        @EnableJpaRepositories(basePackageClasses = AccountLockJpaRepository.class)
        static class AccountLockJpaConfig {

        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnAccountLockPersistence(PersistenceProperties.Type.jdbc)
        static class AccountLockJdbcConfig {
                @Bean
                @ConditionalOnMissingBean(AccountLockRepository.class)
                AccountLockRepository accountLockJdbcRepository(NamedParameterJdbcTemplate template) {
                        return new AccountLockJdbcRepository(template);
                }
        }
}
