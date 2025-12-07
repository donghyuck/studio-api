package studio.one.platform.autoconfigure.features.i18n;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.I18nProperties;
import studio.one.platform.component.I18nImpl;
import studio.one.platform.component.State;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;
import studio.one.platform.web.aop.MessageAspect;

/**
 * i18n.resources 가 지정되면 그 패턴/베이스네임을 사용하고,
 * 지정이 없으면 기본 패턴(classpath*:/i18n/__/messages__.properties)으로 자동 스캔
 * 
 * @author donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties(I18nProperties.class)
@AutoConfigureBefore(MessageSourceAutoConfiguration.class)
@Slf4j
public class I18nAutoConfiguration {

    @Bean(name = ServiceNames.I18N_MESSAGE_SOURCE)
    @ConditionalOnMissingBean(name = ServiceNames.I18N_MESSAGE_SOURCE)
    public MessageSource messageSource(I18nProperties props) throws IOException {
        return ModularMessageSourceFactory.create(props);
    }

    @Bean(ServiceNames.I18N_MESSAGE_ACCESSOR)
    @ConditionalOnMissingBean(name = ServiceNames.I18N_MESSAGE_ACCESSOR)
    public MessageSourceAccessor messageSourceAccessor(@Qualifier(ServiceNames.I18N_MESSAGE_SOURCE) MessageSource ms) {
        return new MessageSourceAccessor(ms);
    }

    @Bean(name = ServiceNames.I18N)
    @ConditionalOnMissingBean(I18n.class)
    public I18n i18n(@Qualifier(ServiceNames.I18N_MESSAGE_ACCESSOR) MessageSourceAccessor accessor) {
        return new I18nImpl(accessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageAspect messageAspecgt(ObjectProvider<I18n> i18nProvider) {
        return new MessageAspect(I18nUtils.resolve(i18nProvider));
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> logServiceState(ConfigurableListableBeanFactory bf,
            ObjectProvider<I18n> i18nProvider) {
        return evt -> {
            I18n i18n = I18nUtils.resolve(i18nProvider);
            String[] serviceBeanNames = bf.getBeanNamesForAnnotation(Service.class);
            for (String beanName : serviceBeanNames) {
                Object obj = bf.getBean(beanName);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, featureString(beanName),
                        LogUtils.blue(simpleClassName(obj)),
                        LogUtils.red(State.CREATED.toString())));
            }
        };
    }

    private String simpleClassName(Object obj){
        String raw = obj.getClass().getSimpleName();
        int idx = raw.indexOf("$$");
        return (idx >= 0) ? raw.substring(0, idx) : raw;      
    }
    private String featureString(String raw) {
        Matcher m = Pattern.compile("features:([^:]+):.*").matcher(raw);
        return m.matches() ? m.group(1).toUpperCase(Locale.ROOT) : "Unknown";
    }
}
