package studio.echo.platform.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import studio.echo.base.security.web.adivce.SecurityExceptionHandler;

@AutoConfiguration
@RequiredArgsConstructor
public class SecurityWebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(studio.echo.platform.service.I18n i18n) {
        return new SecurityExceptionHandler(i18n);  
    }
}
