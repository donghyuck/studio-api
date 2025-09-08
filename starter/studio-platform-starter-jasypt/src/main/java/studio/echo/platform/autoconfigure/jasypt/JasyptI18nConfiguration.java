package studio.echo.platform.autoconfigure.jasypt;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;

import studio.echo.platform.constant.ServiceNames;

@AutoConfiguration
public class JasyptI18nConfiguration {

     /** starter 전용 메시지 소스 */
    @Bean(ServiceNames.JASYPT_MESSAGE_SOURCE)
    public MessageSource jasyptMessageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames("META-INF/i18n/jasypt/messages"); // 아래 2)의 파일 경로와 매칭
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true); // 키가 없으면 키 그대로 출력
        return ms;
    }

    @Bean(ServiceNames.JASYPT_MESSAGE_ACCESSOR)
    public MessageSourceAccessor studioJasyptMessages(@Qualifier(ServiceNames.JASYPT_MESSAGE_SOURCE) MessageSource jasyptMessageSource) {
        return new MessageSourceAccessor(jasyptMessageSource);
    }
}
