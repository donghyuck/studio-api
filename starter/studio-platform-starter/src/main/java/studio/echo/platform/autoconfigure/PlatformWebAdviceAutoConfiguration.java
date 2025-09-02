package studio.echo.platform.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import studio.echo.platform.web.advice.GlobalExceptionHandler;

@AutoConfiguration
@RequiredArgsConstructor
public class PlatformWebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(studio.echo.platform.service.I18n i18n) {
        return new GlobalExceptionHandler(i18n);  
    }
}
