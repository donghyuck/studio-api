package studio.echo.platform.autoconfigure;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.MessageSourceAccessor;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.component.I18nImpl;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.I18n;

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
public class PlatformI18nAutoConfiguration {
 
    @Bean(name = ServiceNames.I18N_MSSSAGE_SOURCE )
    @ConditionalOnMissingBean(name = ServiceNames.I18N_MSSSAGE_SOURCE)
    public MessageSource messageSource(I18nProperties props) throws IOException {
        return ModularMessageSourceFactory.create(props);  
    }

    @Bean(ServiceNames.I18N_MSSSAGE_ACCESSOR)
    @ConditionalOnMissingBean(name = ServiceNames.I18N_MSSSAGE_ACCESSOR)
    public MessageSourceAccessor messageSourceAccessor(@Qualifier(ServiceNames.I18N_MSSSAGE_SOURCE) MessageSource ms) {
        return new MessageSourceAccessor(ms);
    }

    @Bean(name = ServiceNames.I18N)
    @ConditionalOnMissingBean(I18n.class)
    public I18n i18n(@Qualifier(ServiceNames.I18N_MSSSAGE_ACCESSOR) MessageSourceAccessor accessor) {
        return new I18nImpl(accessor); 
    }
}
