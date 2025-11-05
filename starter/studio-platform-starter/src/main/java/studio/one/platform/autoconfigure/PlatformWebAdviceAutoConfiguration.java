package studio.one.platform.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import studio.one.platform.web.advice.GlobalExceptionHandler;

@AutoConfiguration
@RequiredArgsConstructor
public class PlatformWebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(studio.one.platform.service.I18n i18n) {
        return new GlobalExceptionHandler(i18n);  
    }
}
