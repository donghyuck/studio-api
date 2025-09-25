package studio.echo.platform.security.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.audit.LoginFailureLogRepository;
import studio.echo.base.security.authentication.AccountLockService;
import studio.echo.base.security.authentication.AccountLockServiceImpl;
import studio.echo.base.user.domain.repository.ApplicationUserRepository;
import studio.echo.platform.autoconfigure.i18n.I18nKeys;
import studio.echo.platform.component.State;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties(AccountLockProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.Auth.LOCK, name = "enabled", havingValue = "true")
@Slf4j
public class AccountLockAutoConfiguration {

        private static final String FEATURE_NAME = "Security - Account Lock";
        @Bean(ServiceNames.SECURITY_ACCOUNT_LOCK_SERVICE) 
        @ConditionalOnMissingBean(name = ServiceNames.SECURITY_ACCOUNT_LOCK_SERVICE)
        public AccountLockService accountLockService(
                        ApplicationUserRepository aplicationUserRepository,
                        LoginFailureLogRepository failureLogRepo,
                        AccountLockProperties properties,
                        Clock clock,
                        ObjectProvider<I18n> i18nProvider) {

                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(AccountLockService.class, true), LogUtils.red(State.CREATED.toString())));
                return new AccountLockServiceImpl(
                                aplicationUserRepository,
                                failureLogRepo,
                                clock,
                                properties.getMaxAttempts(),
                                properties.getWindow(),
                                properties.getLockDuration(),
                                properties.isResetOnSuccess());
                                
        }

}
