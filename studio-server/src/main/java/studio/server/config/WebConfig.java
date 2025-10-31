package studio.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.web.converter.StringToOffsetDateTimeConverter;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addFormatters(FormatterRegistry registry) {
        log.info("add converter {}", StringToOffsetDateTimeConverter.class.getName());
        registry.addConverter(new StringToOffsetDateTimeConverter());
    }
}
