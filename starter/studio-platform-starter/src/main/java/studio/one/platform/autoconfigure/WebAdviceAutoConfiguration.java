package studio.one.platform.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.State;
import studio.one.platform.constant.MessageCodes;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;
import studio.one.platform.web.advice.GlobalExceptionHandler;

@AutoConfiguration
@RequiredArgsConstructor
@Slf4j
public class WebAdviceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler( ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);          
        log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(GlobalExceptionHandler.class, true),
				LogUtils.red(State.CREATED.toString()))); 
        return new GlobalExceptionHandler(i18n);  
    }
}
