package studio.echo.platform.starter.autoconfig;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.echo.platform.constant.PropertyKeys;

@Getter
@Setter
@ConfigurationProperties(prefix = PropertyKeys.I18n.PREFIX)
public class I18nProperties {
    private List<String> resources = List.of("classpath:/i18n/messages");
    private String endoding = "UTF-8";
    private int cacheSeconds= -1;
    private boolean fallbackToSystemLocale = false;
}
