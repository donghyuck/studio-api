package studio.echo.platform.security.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import studio.echo.base.security.authentication.AccountLockService;
import studio.echo.base.security.web.adivce.SecurityExceptionHandler;

@AutoConfiguration
@RequiredArgsConstructor
public class SecurityWebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(ObjectProvider<AccountLockService> accountLockService, ObjectProvider<Clock> clock, studio.echo.platform.service.I18n i18n) {
        return new SecurityExceptionHandler(accountLockService, clock.getIfAvailable(), i18n); 
    }
}
