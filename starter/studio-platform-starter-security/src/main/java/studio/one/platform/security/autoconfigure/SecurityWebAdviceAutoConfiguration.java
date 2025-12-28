package studio.one.platform.security.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.base.security.web.adivce.SecurityExceptionHandler;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@RequiredArgsConstructor
@Slf4j
public class SecurityWebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(ObjectProvider<AccountLockService> accountLockService, 
        ObjectProvider<Clock> clock, 
        ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);            
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, SecurityAutoConfiguration.FEATURE_NAME,
                                LogUtils.blue(SecurityExceptionHandler.class, true),
                                LogUtils.red(State.CREATED.toString())));

        return new SecurityExceptionHandler(accountLockService, clock.getIfAvailable(), i18n); 
    }
}
